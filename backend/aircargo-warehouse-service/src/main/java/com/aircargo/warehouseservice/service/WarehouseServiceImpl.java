package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.dto.ReceiptPieceDTO;
import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.entity.ReceiptPiece;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.ReceiptPieceRepository;
import com.aircargo.warehouseservice.repository.WarehouseReceiptRepository;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.client.BookingClient;
import com.aircargo.common.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WarehouseServiceImpl implements WarehouseService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WarehouseServiceImpl.class);

    private final WarehouseReceiptRepository receiptRepository;
    private final ReceiptPieceRepository pieceRepository;
    private final MawbClient mawbClient;
    private final BookingClient bookingClient;
    private final ReceiptExportService receiptExportService;
    private final ReceiptFullPdfService receiptFullPdfService;
    private final PdfGenerationService pdfGenerationService;
    private final ObjectMapper objectMapper;
    private final MawbNumberResolver mawbNumberResolver;

    public WarehouseServiceImpl(WarehouseReceiptRepository receiptRepository,
                                 ReceiptPieceRepository pieceRepository,
                                 MawbClient mawbClient,
                                 BookingClient bookingClient,
                                 ReceiptExportService receiptExportService,
                                 ReceiptFullPdfService receiptFullPdfService,
                                 PdfGenerationService pdfGenerationService,
                                 ObjectMapper objectMapper,
                                 MawbNumberResolver mawbNumberResolver) {
        this.receiptRepository = receiptRepository;
        this.pieceRepository = pieceRepository;
        this.mawbClient = mawbClient;
        this.bookingClient = bookingClient;
        this.receiptExportService = receiptExportService;
        this.receiptFullPdfService = receiptFullPdfService;
        this.pdfGenerationService = pdfGenerationService;
        this.objectMapper = objectMapper;
        this.mawbNumberResolver = mawbNumberResolver;
    }

    @Override
    @Transactional
    @CacheEvict(value = {"warehouse-receipts", "receipt-pieces"}, allEntries = true)
    public WarehouseReceiptDTO emitReceipt(WarehouseReceiptDTO dto, UserPrincipal principal, jakarta.servlet.http.HttpServletRequest request) {
        // Set audit fields
        dto.setCreatedByUserId(principal != null ? principal.getUserIdAsUuid() : null);
        dto.setCreatedByName(principal != null ? principal.fullName() : "system");
        dto.setCreatedAt(OffsetDateTime.now());
        dto.setReceiptDate(OffsetDateTime.now());
        dto.setSuperseded(false);

        // Resolve MAWB number: stored value → Feign → shared-DB fallback
        dto.setMawbNumber(mawbNumberResolver.resolve(dto.getMawbId(), dto.getMawbNumber()));

        // Process pieces
        List<ReceiptPieceDTO> pieces = processPieces(dto.getPieces(), dto.getDimFactorDom(), dto.getDimFactorIntl());
        dto.setPieces(pieces);
        dto.setPieceCount(pieces.stream().mapToInt(p -> p.getPieces() != null ? p.getPieces() : 1).sum());

        // Calculate totals
        calculateTotals(dto);

        // Save receipt FIRST to get a real ID
        WarehouseReceipt entity = WarehouseReceiptDTO.toEntity(dto);
        WarehouseReceipt saved = receiptRepository.save(entity);

        // Supersede existing receipts for this MAWB — must happen AFTER save so excludeId is not null
        if (saved.getMawbId() != null) {
            receiptRepository.supersedeAllByMawbId(saved.getMawbId(), saved.getId());
        }

        // Save pieces
        List<ReceiptPiece> pieceEntities = pieces.stream()
                .map(p -> ReceiptPieceDTO.toEntity(p))
                .peek(e -> e.setReceiptId(saved.getId()))
                .toList();
        pieceRepository.saveAll(pieceEntities);

        // Generate async artifacts
        generatePersistedArtifacts(saved.getId());

        // Sync MAWB and booking
        syncMawbAndBooking(saved);

        // Auto-set MAWB status to RECEIVED if currently BOOKED
        updateMawbStatusToReceived(saved.getMawbId());

        // Publish event
        publishReceiptCreatedEvent(saved);

        return WarehouseReceiptDTO.fromEntity(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"warehouse-receipts", "receipt-pieces"}, allEntries = true)
    public Optional<WarehouseReceiptDTO> updateReceipt(UUID receiptId,
                                                        WarehouseReceiptDTO dto,
                                                        UserPrincipal principal,
                                                        jakarta.servlet.http.HttpServletRequest request) {
        return receiptRepository.findById(receiptId).map(existing -> {
            // Update fields
            existing.setShipperName(dto.getShipperName());
            existing.setConsigneeName(dto.getConsigneeName());
            existing.setOrigin(dto.getOrigin());
            existing.setDestination(dto.getDestination());
            existing.setAwbReportedPieces(dto.getAwbReportedPieces());
            existing.setMawbWeightGreatest(dto.getMawbWeightGreatest());
            existing.setShipperReportedWeight(dto.getShipperReportedWeight());
            existing.setCashOnly(dto.getCashOnly());
            existing.setBookedInAcoms(dto.getBookedInAcoms());
            existing.setDocsProvided(dto.getDocsProvided());
            existing.setCustomsCompleted(dto.getCustomsCompleted());
            existing.setPreBuilt(dto.getPreBuilt());
            existing.setLooseTender(dto.getLooseTender());
            existing.setShipperComment(dto.getShipperComment());
            existing.setObservations(dto.getObservations());
            existing.setRemarks(dto.getRemarks());
            existing.setCreatedByName(dto.getCreatedByName());
            existing.setDeliveredByName(dto.getDeliveredByName());
            existing.setDeliveredByIdNum(dto.getDeliveredByIdNum());
            existing.setDeliveredByIdDocUrl(dto.getDeliveredByIdDocUrl());
            existing.setDeliveredBySigUrl(dto.getDeliveredBySigUrl());
            existing.setReceivedByName(dto.getReceivedByName());
            existing.setReceivedByIdNum(dto.getReceivedByIdNum());
            existing.setReceivedByIdDocUrl(dto.getReceivedByIdDocUrl());
            existing.setReceivedBySigUrl(dto.getReceivedBySigUrl());
            existing.setBrokerName(dto.getBrokerName());
            existing.setBrokerIdNum(dto.getBrokerIdNum());
            existing.setBrokerIdDocUrl(dto.getBrokerIdDocUrl());
            existing.setBrokerSigUrl(dto.getBrokerSigUrl());
            existing.setReceiptDocUrl(dto.getReceiptDocUrl());
            existing.setDockSignature(dto.getDockSignature());
            existing.setSupportingDocs(dto.getSupportingDocs());
            existing.setPrintName(dto.getPrintName());
            existing.setCorrectionReason(dto.getCorrectionReason());
            existing.setCorrectedByName(dto.getCorrectedByName());
            existing.setDimFactorDom(dto.getDimFactorDom());
            existing.setDimFactorIntl(dto.getDimFactorIntl());

            // Update pieces
            pieceRepository.deleteByReceiptId(existing.getId());
            List<ReceiptPieceDTO> pieces = processPieces(dto.getPieces(), dto.getDimFactorDom(), dto.getDimFactorIntl());
            dto.setPieces(pieces);
            calculateTotals(dto);

            List<ReceiptPiece> pieceEntities = pieces.stream()
                    .map(p -> ReceiptPieceDTO.toEntity(p))
                    .peek(e -> e.setReceiptId(existing.getId()))
                    .toList();
            pieceRepository.saveAll(pieceEntities);

            // Copy totals to entity before save
            existing.setPieceCount(dto.getPieceCount());
            existing.setActualWeightLbs(dto.getActualWeightLbs());
            existing.setActualWeightKg(dto.getActualWeightKg());
            existing.setChargeableWeightLbs(dto.getChargeableWeightLbs());
            existing.setChargeableWeightKg(dto.getChargeableWeightKg());

            WarehouseReceipt saved = receiptRepository.save(existing);

            // Regenerate artifacts
            generatePersistedArtifacts(existing.getId());

            // Sync
            syncMawbAndBooking(existing);

            // Auto-set MAWB status to RECEIVED if currently BOOKED
            updateMawbStatusToReceived(existing.getMawbId());

            return WarehouseReceiptDTO.fromEntity(saved);
        });
    }

    @Override
    public WarehouseReceiptDTO validateReceipt(WarehouseReceiptDTO dto) {
        List<ReceiptPieceDTO> pieces = processPieces(dto.getPieces(), dto.getDimFactorDom(), dto.getDimFactorIntl());
        dto.setPieces(pieces);
        calculateTotals(dto);
        return dto;
    }

    @Override
    @Cacheable("receipt-pieces")
    public List<ReceiptPieceDTO> getPieces(UUID receiptId) {
        return pieceRepository.findByReceiptId(receiptId).stream()
                .map(ReceiptPieceDTO::fromEntity)
                .toList();
    }

    @Override
    public String getSupportingDocsJson(UUID receiptId) {
        return receiptRepository.findById(receiptId)
                .map(WarehouseReceipt::getSupportingDocs)
                .orElse("[]");
    }

    @Override
    public String getSupportingDocsHtml(UUID receiptId) {
        WarehouseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));

        String rawDocs = receipt.getSupportingDocs();
        if (rawDocs == null || rawDocs.isBlank() || "[]".equals(rawDocs)) {
            return "<html><body style='font-family:monospace;padding:2rem;color:#333'><h2>Sin evidencias documentales</h2><p>Este recibo no tiene documentos de soporte asociados.</p></body></html>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        sb.append("<title>Evidencias Documentales - ").append(receiptId.toString().substring(0, 8)).append("</title>");
        sb.append("<style>");
        sb.append("*{margin:0;padding:0;box-sizing:border-box}");
        sb.append("body{font-family:'Courier New',monospace;background:#f5f5f5;color:#1a1a1a;padding:2rem}");
        sb.append(".header{max-width:900px;margin:0 auto 2rem;padding:1.5rem;background:#fff;border:1px solid #ddd;border-left:4px solid #1a1a1a}");
        sb.append(".header h1{font-size:1.2rem;text-transform:uppercase;letter-spacing:0.05em}");
        sb.append(".header p{font-size:0.75rem;color:#666;margin-top:0.25rem}");
        sb.append(".grid{max-width:900px;margin:0 auto;display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:1rem}");
        sb.append(".card{background:#fff;border:1px solid #e0e0e0;border-radius:4px;overflow:hidden;break-inside:avoid}");
        sb.append(".card img{width:100%;height:200px;object-fit:cover;display:block;border-bottom:1px solid #eee}");
        sb.append(".card .doc-icon{width:100%;height:120px;display:flex;align-items:center;justify-content:center;background:#fafafa;border-bottom:1px solid #eee;font-size:2.5rem;color:#999}");
        sb.append(".card .info{padding:0.6rem;font-size:0.7rem;color:#555;text-transform:uppercase;letter-spacing:0.03em}");
        sb.append(".footer{max-width:900px;margin:2rem auto 0;padding:1rem 1.5rem;background:#fff;border:1px solid #ddd;font-size:0.65rem;color:#999;text-align:center;text-transform:uppercase;letter-spacing:0.05em}");
        sb.append("@media print{body{background:#fff;padding:0}.header,.card,.footer{border-color:#ccc;box-shadow:none}.card{break-inside:avoid}}");
        sb.append("</style></head><body>");

        String mawbNum = receipt.getMawbNumber() != null ? receipt.getMawbNumber() : "\u2014";
        sb.append("<div class='header'><h1>Evidencias Documentales</h1>");
        sb.append("<p>Recibo: ").append(receiptId.toString().substring(0, 8)).append(" &middot; MAWB: ").append(mawbNum);
        sb.append(" &middot; ").append(receipt.getShipperName() != null ? receipt.getShipperName() : "").append("</p></div>");

        sb.append("<div class='grid'>");
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> docs = objectMapper.readValue(rawDocs, List.class);
            for (Map<String, String> doc : docs) {
                String name = doc.getOrDefault("name", "Documento");
                String type = doc.getOrDefault("type", "document");
                String url = doc.getOrDefault("url", "");
                if ("document".equals(type) && url != null && url.startsWith("data:application/pdf")) {
                    String base64Data = url.substring(url.indexOf(',') + 1);
                    List<String> pageImages = pdfGenerationService.pdfPagesToDataUris(base64Data);
                    if (!pageImages.isEmpty()) {
                        for (int pi = 0; pi < pageImages.size(); pi++) {
                            sb.append("<div class='card'>");
                            sb.append("<img src='").append(pageImages.get(pi)).append("' alt='").append(name).append("' loading='lazy' />");
                            sb.append("<div class='info'>").append(name).append(" (p\u00e1gina ").append(pi + 1).append(")").append("</div>");
                            sb.append("</div>");
                        }
                    } else {
                        sb.append("<div class='card'>");
                        sb.append("<div class='doc-icon'>&#128196;</div>");
                        sb.append("<div class='info'>").append(name).append("</div>");
                        sb.append("</div>");
                    }
                } else if ("image".equals(type) && url != null && !url.isEmpty()) {
                    sb.append("<div class='card'>");
                    sb.append("<img src='").append(url).append("' alt='").append(name).append("' loading='lazy' />");
                    sb.append("<div class='info'>").append(name).append("</div>");
                    sb.append("</div>");
                } else {
                    sb.append("<div class='card'>");
                    sb.append("<div class='doc-icon'>&#128196;</div>");
                    sb.append("<div class='info'>").append(name).append("</div>");
                    sb.append("</div>");
                }
            }
        } catch (Exception e) {
            sb.append("<p>Error al procesar evidencias</p>");
        }
        sb.append("</div>");
        sb.append(buildHawbBreakdownHtml(receipt, false));
        sb.append("<div class='footer'>AirCargo &mdash; Documento generado el ").append(java.time.LocalDateTime.now().toString().replace("T", " ").substring(0, 16)).append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    @Override
    public byte[] getSupportingDocsPdf(UUID receiptId) {
        WarehouseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'/>");
        sb.append("<meta name='viewport' content='width=device-width,initial-scale=1.0'/>");
        sb.append("<title>Evidencias - ").append(receiptId.toString().substring(0, 8)).append("</title>");
        sb.append("<style>");
        sb.append("@page{margin:1cm}");
        sb.append("body{font-family:'JetBrains Mono',Helvetica,Arial,sans-serif;color:#1a1a1a;font-size:10pt;margin:0;padding:0}");
        sb.append(".page{page-break-after:always;display:flex;flex-direction:column;align-items:center;min-height:100vh;box-sizing:border-box;padding:0.5cm 1cm 1cm}");
        sb.append(".page:last-child{page-break-after:auto}");
        sb.append(".page-header{width:100%;border-bottom:2px solid #333;padding-bottom:0.3cm;margin-bottom:0.5cm;text-align:center}");
        sb.append(".page-header h2{font-size:12pt;margin:0;color:#1a1a1a}");
        sb.append(".page-header .meta{font-size:7pt;color:#555;margin-top:0.15cm}");
        sb.append(".page img{display:block;margin:0 auto;max-width:100%;max-height:75vh;object-fit:contain}");
        sb.append(".page .placeholder{font-size:10pt;color:#999;text-align:center;padding:3cm 1cm}");
        sb.append(".footer-text{font-size:7pt;color:#999;text-align:center;margin-top:auto;padding-top:0.5cm;width:100%;border-top:1px solid #ddd}");
        sb.append("</style></head><body>");

        String rawDocs = receipt.getSupportingDocs();
        if (rawDocs == null || rawDocs.isBlank() || "[]".equals(rawDocs)) {
            sb.append("<div class='page'><div class='page-header'><h2>Evidencias Documentales</h2></div>");
            sb.append("<div class='placeholder'>Sin evidencias registradas</div>");
            sb.append("<div class='footer-text'>AirCargo &#8212; ").append(java.time.LocalDateTime.now().toString().replace("T", " ").substring(0, 16)).append("</div>");
            sb.append("</div>");
            sb.append("</body></html>");
            return pdfGenerationService.generatePdf(sb.toString());
        }

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> docs = objectMapper.readValue(rawDocs, List.class);
            String mawbNum = receipt.getMawbNumber() != null ? receipt.getMawbNumber() : "\u2014";

            for (int i = 0; i < docs.size(); i++) {
                Map<String, String> doc = docs.get(i);
                String name = doc.getOrDefault("name", "Documento " + (i + 1));
                String type = doc.getOrDefault("type", "document");
                String url = doc.getOrDefault("url", "");

                sb.append("<div class='page'>");
                sb.append("<div class='page-header'><h2>Evidencias Documentales</h2>");
                sb.append("<div class='meta'>").append(xmlEscape(name)).append(" &#183; MAWB: ").append(xmlEscape(mawbNum)).append("</div>");
                sb.append("</div>");

                if ("image".equals(type) && url != null && !url.isEmpty()) {
                    sb.append("<img src='").append(url).append("' alt='").append(name).append("' />");
                } else if ("document".equals(type) && url != null && url.startsWith("data:application/pdf")) {
                    String base64Data = url.substring(url.indexOf(',') + 1);
                    List<String> pageImages = pdfGenerationService.pdfPagesToDataUris(base64Data);
                    if (!pageImages.isEmpty()) {
                        sb.append("<img src='").append(pageImages.get(0)).append("' alt='").append(name).append("' />");
                    } else {
                        sb.append("<div class='placeholder'>PDF sin p\u00e1ginas renderizables</div>");
                    }
                } else {
                    sb.append("<div class='placeholder'>Documento: ").append(name).append("</div>");
                }

                sb.append("<div class='footer-text'>AirCargo &#8212; P\u00e1gina ").append(i + 1).append(" de ").append(docs.size());
                sb.append(" &#8212; ").append(java.time.LocalDateTime.now().toString().replace("T", " ").substring(0, 16));
                sb.append("</div></div>");
            }
        } catch (Exception e) {
            sb.append("<div class='page'><div class='page-header'><h2>Error</h2></div>");
            sb.append("<div class='placeholder'>Error al procesar evidencias: ").append(e.getMessage()).append("</div>");
            sb.append("</div>");
        }

        sb.append(buildHawbBreakdownHtml(receipt, true));
        sb.append("</body></html>");
        return pdfGenerationService.generatePdf(sb.toString());
    }

    @Override
    public byte[] exportReceipt(UUID receiptId) {
        return receiptExportService.generateAndPersistExcel(receiptId);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String fmt(BigDecimal v) {
        if (v == null) return "0";
        return v.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String dimPart(BigDecimal v) {
        if (v == null) return "\u2014";
        return v.stripTrailingZeros().toPlainString();
    }

    private String buildHawbBreakdownHtml(WarehouseReceipt receipt, boolean forPdf) {
        if (receipt.getMawbId() == null) return "";
        List<ReceiptPiece> pieces = pieceRepository.findByReceiptId(receipt.getId());
        List<ReceiptPiece> hawbPieces = pieces.stream()
                .filter(p -> p.getHawbId() != null)
                .collect(Collectors.toList());
        if (hawbPieces.isEmpty()) return "";

        Map<String, String> hawbNumbers = new HashMap<>();
        Map<String, String> hawbConsignees = new HashMap<>();
        Map<String, String> hawbDestinations = new HashMap<>();
        try {
            List<com.aircargo.feign.dto.HawbDTO> hawbs = mawbClient.getHawbsByMawb(receipt.getMawbId());
            if (hawbs != null) {
                for (var h : hawbs) {
                    if (h.getId() == null) continue;
                    hawbNumbers.put(h.getId().toString(), h.getHawbNumber() != null ? h.getHawbNumber() : "");
                    hawbConsignees.put(h.getId().toString(), h.getConsigneeName() != null ? h.getConsigneeName() : "");
                    hawbDestinations.put(h.getId().toString(), h.getDestination() != null ? h.getDestination() : "");
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener HAWBs del MAWB {}: {}", receipt.getMawbId(), e.getMessage());
        }

        for (ReceiptPiece p : hawbPieces) {
            String key = p.getHawbId().toString();
            if (hawbNumbers.containsKey(key)) continue;
            try {
                com.aircargo.feign.dto.HawbDTO h = mawbClient.getHawbById(p.getHawbId());
                if (h != null && h.getId() != null) {
                    hawbNumbers.put(key, h.getHawbNumber() != null ? h.getHawbNumber() : "");
                    hawbConsignees.put(key, h.getConsigneeName() != null ? h.getConsigneeName() : "");
                    hawbDestinations.put(key, h.getDestination() != null ? h.getDestination() : "");
                }
            } catch (Exception e) {
                log.warn("No se pudo resolver HAWB {}: {}", key, e.getMessage());
            }
        }

        String receiptConsignee = receipt.getConsigneeName() != null ? receipt.getConsigneeName() : "";
        String receiptDestination = receipt.getDestination() != null ? receipt.getDestination() : "";

        LinkedHashMap<UUID, List<ReceiptPiece>> groups = new LinkedHashMap<>();
        for (ReceiptPiece p : hawbPieces) {
            groups.computeIfAbsent(p.getHawbId(), k -> new ArrayList<>()).add(p);
        }

        StringBuilder sb = new StringBuilder();
        if (forPdf) {
            sb.append("<div class='page' style='page-break-before:always'>");
            sb.append("<div class='page-header'><h2>Desglose de HAWBs</h2>");
            sb.append("<div class='meta'>MAWB: ").append(xmlEscape(receipt.getMawbNumber() != null ? receipt.getMawbNumber() : "")).append("</div></div>");
        } else {
            sb.append("<div class='hawb-breakdown' style='max-width:900px;margin:1.5rem auto 0;background:#fff;border:1px solid #ddd;border-left:4px solid #1a1a1a;padding:1rem'>");
            sb.append("<h3 style='margin:0 0 0.5rem;text-transform:uppercase;letter-spacing:0.05em;font-size:0.9rem'>Desglose de HAWBs</h3>");
            sb.append("<p style='font-size:0.7rem;color:#666;margin:0 0 0.75rem'>MAWB: ").append(xmlEscape(receipt.getMawbNumber() != null ? receipt.getMawbNumber() : "")).append("</p>");
        }

        sb.append("<table style='width:100%;border-collapse:collapse;font-size:7.5pt'>");
        sb.append("<thead><tr>");
        String[] headers = {"HAWB", "Consignatario", "Dest.", "Pzas", "Dim (L\u00d7A\u00d7H in)", "Balanza lbs", "Volum. lbs", "Cobrable lbs"};
        for (String h : headers) {
            sb.append("<th style='border:1px solid #333;padding:3px;text-align:left;background:#eee'>").append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");

        int totalPcs = 0;
        BigDecimal totalScale = BigDecimal.ZERO;
        BigDecimal totalDim = BigDecimal.ZERO;
        BigDecimal totalChargeable = BigDecimal.ZERO;

        for (var entry : groups.entrySet()) {
            String key = entry.getKey().toString();
            String num = hawbNumbers.getOrDefault(key, key.substring(0, Math.min(8, key.length())));
            String consignee = hawbConsignees.getOrDefault(key, "");
            String dest = hawbDestinations.getOrDefault(key, "");
            if (consignee.isBlank()) consignee = receiptConsignee;
            if (dest.isBlank()) dest = receiptDestination;
            List<ReceiptPiece> g = entry.getValue();
            int gPcs = 0;
            BigDecimal gScale = BigDecimal.ZERO;
            BigDecimal gDim = BigDecimal.ZERO;
            BigDecimal gChargeable = BigDecimal.ZERO;

            for (ReceiptPiece p : g) {
                int pcs = p.getPieces() != null ? p.getPieces() : 1;
                gPcs += pcs;
                BigDecimal scale = nz(p.getScaleWeightLbs());
                BigDecimal dim = nz(p.getDimWeightLbs());
                BigDecimal chg = nz(p.getChargeableLbs());
                gScale = gScale.add(scale);
                gDim = gDim.add(dim);
                gChargeable = gChargeable.add(chg);

                sb.append("<tr>");
                sb.append("<td style='border:1px solid #ccc;padding:3px;font-weight:bold'>").append(xmlEscape(num)).append("</td>");
                sb.append("<td style='border:1px solid #ccc;padding:3px'>").append(xmlEscape(consignee)).append("</td>");
                sb.append("<td style='border:1px solid #ccc;padding:3px'>").append(xmlEscape(dest)).append("</td>");
                sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:center'>").append(pcs).append("</td>");
                sb.append("<td style='border:1px solid #ccc;padding:3px;white-space:nowrap'>")
                        .append(dimPart(p.getLengthIn())).append(" \u00d7 ").append(dimPart(p.getWidthIn()))
                        .append(" \u00d7 ").append(dimPart(p.getHeightIn())).append("</td>");
                sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:right'>").append(fmt(scale)).append("</td>");
                sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:right'>").append(fmt(dim)).append("</td>");
                sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:right'>").append(fmt(chg)).append("</td>");
                sb.append("</tr>");
            }

            sb.append("<tr style='background:#f5f5f5'>");
            sb.append("<td colspan='3' style='border:1px solid #ccc;padding:3px;font-weight:bold;text-align:right'>Subtotal ").append(xmlEscape(num)).append("</td>");
            sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:center;font-weight:bold'>").append(gPcs).append("</td>");
            sb.append("<td style='border:1px solid #ccc;padding:3px'></td>");
            sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:right;font-weight:bold'>").append(fmt(gScale)).append("</td>");
            sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:right;font-weight:bold'>").append(fmt(gDim)).append("</td>");
            sb.append("<td style='border:1px solid #ccc;padding:3px;text-align:right;font-weight:bold'>").append(fmt(gChargeable)).append("</td>");
            sb.append("</tr>");

            totalPcs += gPcs;
            totalScale = totalScale.add(gScale);
            totalDim = totalDim.add(gDim);
            totalChargeable = totalChargeable.add(gChargeable);
        }

        sb.append("<tr style='background:#e8e8e8'>");
        sb.append("<td colspan='3' style='border:1px solid #333;padding:3px;font-weight:bold;text-align:right'>TOTAL</td>");
        sb.append("<td style='border:1px solid #333;padding:3px;text-align:center;font-weight:bold'>").append(totalPcs).append("</td>");
        sb.append("<td style='border:1px solid #333;padding:3px'></td>");
        sb.append("<td style='border:1px solid #333;padding:3px;text-align:right;font-weight:bold'>").append(fmt(totalScale)).append("</td>");
        sb.append("<td style='border:1px solid #333;padding:3px;text-align:right;font-weight:bold'>").append(fmt(totalDim)).append("</td>");
        sb.append("<td style='border:1px solid #333;padding:3px;text-align:right;font-weight:bold'>").append(fmt(totalChargeable)).append("</td>");
        sb.append("</tr></tbody></table>");

        if (forPdf) {
            sb.append("<div class='footer-text'>AirCargo &#8212; Desglose de HAWBs</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    @Override
    public String getExportUrl(UUID receiptId) {
        return "/api/warehouse/receipts/" + receiptId + "/export";
    }

    @Override
    public byte[] getReceiptPdf(UUID receiptId) {
        return receiptFullPdfService.generateReceiptPdf(receiptId);
    }

    private List<ReceiptPieceDTO> processPieces(List<ReceiptPieceDTO> pieces,
                                                 Integer dimFactorDom, Integer dimFactorIntl) {
        if (pieces == null || pieces.isEmpty()) return List.of();
        int factor = dimFactorDom != null ? dimFactorDom : 194;
        return pieces.stream()
                .peek(p -> calculatePieceWeights(p, factor))
                .collect(Collectors.toList());
    }

    private void calculatePieceWeights(ReceiptPieceDTO piece, int dimFactor) {
        if (piece.getLengthIn() != null && piece.getWidthIn() != null && piece.getHeightIn() != null) {
            BigDecimal pieces = piece.getPieces() != null ? BigDecimal.valueOf(piece.getPieces()) : BigDecimal.ONE;
            BigDecimal volume = piece.getLengthIn().multiply(piece.getWidthIn())
                    .multiply(piece.getHeightIn()).multiply(pieces);
            BigDecimal dimWeightLbs = volume.divide(BigDecimal.valueOf(dimFactor), 2, BigDecimal.ROUND_HALF_UP);
            piece.setDimWeightLbs(dimWeightLbs);
            piece.setDimWeightKg(dimWeightLbs.multiply(BigDecimal.valueOf(0.45359237)).setScale(3, BigDecimal.ROUND_HALF_UP));
        }
        BigDecimal scaleLbs = piece.getScaleWeightLbs() != null ? piece.getScaleWeightLbs() : BigDecimal.ZERO;
        BigDecimal dimLbs = piece.getDimWeightLbs() != null ? piece.getDimWeightLbs() : BigDecimal.ZERO;
        BigDecimal chargeableLbs = scaleLbs.max(dimLbs);
        piece.setChargeableLbs(chargeableLbs);
        piece.setChargeableKg(chargeableLbs.multiply(BigDecimal.valueOf(0.45359237)).setScale(3, BigDecimal.ROUND_HALF_UP));
    }

    private void calculateTotals(WarehouseReceiptDTO dto) {
        if (dto.getPieces() == null || dto.getPieces().isEmpty()) {
            dto.setActualWeightLbs(BigDecimal.ZERO);
            dto.setActualWeightKg(BigDecimal.ZERO);
            dto.setChargeableWeightLbs(BigDecimal.ZERO);
            dto.setChargeableWeightKg(BigDecimal.ZERO);
            dto.setPieceCount(0);
            return;
        }

        int pieceCount = dto.getPieces().stream().mapToInt(p -> p.getPieces() != null ? p.getPieces() : 1).sum();
        BigDecimal totalScaleLbs = dto.getPieces().stream()
                .map(p -> p.getScaleWeightLbs() != null ? p.getScaleWeightLbs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalChargeableLbs = dto.getPieces().stream()
                .map(p -> p.getChargeableLbs() != null ? p.getChargeableLbs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dto.setPieceCount(pieceCount);
        dto.setActualWeightLbs(totalScaleLbs);
        dto.setActualWeightKg(totalScaleLbs.multiply(BigDecimal.valueOf(0.45359237)).setScale(3, BigDecimal.ROUND_HALF_UP));
        dto.setChargeableWeightLbs(totalChargeableLbs);
        dto.setChargeableWeightKg(totalChargeableLbs.multiply(BigDecimal.valueOf(0.45359237)).setScale(3, BigDecimal.ROUND_HALF_UP));
    }

    @Async
    public void generatePersistedArtifacts(UUID receiptId) {
        try {
            receiptRepository.findById(receiptId).ifPresent(receipt -> {
                receiptExportService.generateAndPersistExcel(receiptId);
                receiptFullPdfService.generateReceiptPdf(receiptId);
            });
        } catch (Exception e) {
            // Log error
        }
    }

    private void supersedeExistingReceipts(UUID mawbId, UUID newReceiptId) {
        if (mawbId != null) {
            receiptRepository.supersedeAllByMawbId(mawbId, newReceiptId);
        }
    }

    private void syncMawbAndBooking(WarehouseReceipt receipt) {
        try {
            if (receipt.getMawbId() != null) {
                var booking = bookingClient.getBookingByMawbId(receipt.getMawbId());
                if (booking != null && (booking.getAwbNumber() == null || booking.getAwbNumber().isBlank())
                        && receipt.getMawbNumber() != null && !receipt.getMawbNumber().isBlank()) {
                    bookingClient.updateBookingAwb(booking.getId(), Map.of("awbNumber", receipt.getMawbNumber()));
                }
            }
        } catch (Exception e) {
            // Log but don't fail
        }
    }

    private void updateMawbStatusToReceived(UUID mawbId) {
        if (mawbId == null) return;
        try {
            var mawb = mawbClient.getMawbById(mawbId);
            if (mawb != null && "BOOKED".equals(mawb.getStatus())) {
                mawbClient.updateMawbStatus(mawbId, "RECEIVED");
                log.info("Auto-set MAWB {} status to RECEIVED", mawbId);
            }
        } catch (Exception e) {
            log.warn("Failed to auto-set MAWB {} status to RECEIVED: {}", mawbId, e.getMessage());
        }
    }

    private void publishReceiptCreatedEvent(WarehouseReceipt receipt) {
        log.warn("RabbitMQ not available - event not published: receipt.created");
    }
}