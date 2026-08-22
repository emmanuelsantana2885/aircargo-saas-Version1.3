package com.aircargo.mawbservice.service;

import com.aircargo.common.dto.PageResponse;
import com.aircargo.mawbservice.dto.MawbDTO;
import com.aircargo.mawbservice.entity.Mawb;
import com.aircargo.mawbservice.entity.MawbStatus;
import com.aircargo.mawbservice.repository.MawbRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MawbServiceImpl implements MawbService {

    private static final Logger log = LoggerFactory.getLogger(MawbServiceImpl.class);

    private final MawbRepository mawbRepository;
    private final ObjectMapper objectMapper;

    public MawbServiceImpl(MawbRepository mawbRepository, ObjectMapper objectMapper) {
        this.mawbRepository = mawbRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MawbDTO> getAll(UUID airlineId, UUID flightId, MawbStatus status) {
        List<Mawb> results;
        if (airlineId != null && flightId != null && status != null) {
            results = mawbRepository.findByAirlineIdAndFlightIdAndStatus(airlineId, flightId, status);
        } else if (airlineId != null && flightId != null) {
            results = mawbRepository.findByAirlineIdAndFlightId(airlineId, flightId);
        } else if (airlineId != null && status != null) {
            results = mawbRepository.findByAirlineIdAndStatus(airlineId, status);
        } else if (airlineId != null) {
            results = mawbRepository.findByAirlineId(airlineId);
        } else if (flightId != null) {
            results = mawbRepository.findByFlightId(flightId);
        } else {
            results = mawbRepository.findAll();
        }
        return results.stream().map(MawbDTO::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MawbDTO> getAll(UUID airlineId, UUID flightId, MawbStatus status, int page, int size) {
        PageRequest pageReq = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<Mawb> results;
        if (airlineId != null && flightId != null && status != null) {
            results = mawbRepository.findByAirlineIdAndFlightIdAndStatus(airlineId, flightId, status, pageReq);
        } else if (airlineId != null && flightId != null) {
            results = mawbRepository.findByAirlineIdAndFlightId(airlineId, flightId, pageReq);
        } else if (airlineId != null && status != null) {
            results = mawbRepository.findByAirlineIdAndStatus(airlineId, status, pageReq);
        } else if (airlineId != null) {
            results = mawbRepository.findByAirlineId(airlineId, pageReq);
        } else if (flightId != null) {
            results = mawbRepository.findByFlightId(flightId, pageReq);
        } else {
            results = mawbRepository.findAll(pageReq);
        }
        List<MawbDTO> dtoList = results.getContent().stream()
                .map(MawbDTO::fromEntity).collect(Collectors.toList());
        return PageResponse.of(dtoList, page, size, results.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "mawbs", key = "#id")
    public Optional<MawbDTO> getById(UUID id) {
        return mawbRepository.findById(id).map(MawbDTO::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MawbDTO> getByAwbNumber(String awbNumber) {
        return mawbRepository.findByAwbNumber(awbNumber).map(MawbDTO::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MawbDTO> getByFlightId(UUID flightId) {
        return mawbRepository.findByFlightId(flightId).stream()
                .map(MawbDTO::fromEntity).toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "mawbs", allEntries = true)
    public MawbDTO create(MawbDTO dto) {
        Mawb entity = MawbDTO.toEntity(dto);
        if (entity.getStatus() == null) {
            entity.setStatus(MawbStatus.BOOKED);
        }
        Mawb saved = mawbRepository.save(entity);
        return MawbDTO.fromEntity(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "mawbs", allEntries = true)
    public Optional<MawbDTO> update(UUID id, MawbDTO dto) {
        return mawbRepository.findById(id)
                .map(existing -> {
                    if (dto.getAirlineId() != null) existing.setAirlineId(dto.getAirlineId());
                    if (dto.getFlightId() != null) existing.setFlightId(dto.getFlightId());
                    if (dto.getAwbNumber() != null) existing.setAwbNumber(dto.getAwbNumber());
                    if (dto.getShipperName() != null) existing.setShipperName(dto.getShipperName());
                    if (dto.getConsigneeName() != null) existing.setConsigneeName(dto.getConsigneeName());
                    if (dto.getOrigin() != null) existing.setOrigin(dto.getOrigin());
                    if (dto.getDestination() != null) existing.setDestination(dto.getDestination());
                    if (dto.getPieces() != null) existing.setPieces(dto.getPieces());
                    if (dto.getReportedWeightKg() != null) existing.setReportedWeightKg(dto.getReportedWeightKg());
                    if (dto.getChargeableWeightKg() != null) existing.setChargeableWeightKg(dto.getChargeableWeightKg());
                    if (dto.getCommodityType() != null) existing.setCommodityType(dto.getCommodityType());
                    if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
                    if (dto.getCashOnly() != null) existing.setCashOnly(dto.getCashOnly());
                    if (dto.getBookedInAcoms() != null) existing.setBookedInAcoms(dto.getBookedInAcoms());
                    if (dto.getDocsProvided() != null) existing.setDocsProvided(dto.getDocsProvided());
                    if (dto.getCustomsCompleted() != null) existing.setCustomsCompleted(dto.getCustomsCompleted());
                    if (dto.getPreBuilt() != null) existing.setPreBuilt(dto.getPreBuilt());
                    if (dto.getLooseTender() != null) existing.setLooseTender(dto.getLooseTender());
                    if (dto.getSupportingDocs() != null) existing.setSupportingDocs(dto.getSupportingDocs());
                    if (dto.getNotes() != null) existing.setNotes(dto.getNotes());
                    return mawbRepository.save(existing);
                })
                .map(MawbDTO::fromEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "mawbs", allEntries = true)
    public Optional<MawbDTO> updateStatus(UUID id, MawbStatus status) {
        return mawbRepository.findById(id)
                .map(existing -> {
                    MawbStatus oldStatus = existing.getStatus();
                    existing.setStatus(status);
                    Mawb saved = mawbRepository.save(existing);
                    if (oldStatus != status) {
                        publishStatusChanged(saved, oldStatus, status);
                    }
                    return saved;
                })
                .map(MawbDTO::fromEntity);
    }

    private void publishStatusChanged(Mawb mawb, MawbStatus oldStatus, MawbStatus newStatus) {
        log.warn("RabbitMQ not available - event not published: mawb.status.changed");
    }

    @Override
    @Transactional
    @CacheEvict(value = "mawbs", allEntries = true)
    public void updateSupportingDocs(UUID id, Map<String, Object> body) {
        mawbRepository.findById(id).ifPresent(mawb -> {
            try {
                String json = objectMapper.writeValueAsString(body);
                mawb.setSupportingDocs(json);
                mawbRepository.save(mawb);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize supporting docs", e);
            }
        });
    }

    @Override
    @Transactional
    @CacheEvict(value = "mawbs", allEntries = true)
    public boolean delete(UUID id) {
        if (!mawbRepository.existsById(id)) return false;
        mawbRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getSupportingDocsPdf(UUID id) {
        Optional<Mawb> opt = mawbRepository.findById(id);
        if (opt.isEmpty()) return null;
        Mawb mawb = opt.get();
        try {
            String html = buildSupportingDocsHtml(mawb);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            var builder = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSupportingDocsHtml(Mawb mawb) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'/><style>")
          .append("body{font-family:sans-serif;padding:20px}")
          .append("h1{font-size:16px;margin-bottom:10px}")
          .append("table{width:100%;border-collapse:collapse}")
          .append("td,th{border:1px solid #333;padding:6px;font-size:12px}")
          .append("img{max-width:200px;max-height:200px}")
          .append("</style></head><body>")
          .append("<h1>MAWB: ").append(xmlEscape(mawb.getAwbNumber())).append("</h1>")
          .append("<p>Shipper: ").append(xmlEscape(mawb.getShipperName())).append("</p>")
          .append("<p>Consignee: ").append(xmlEscape(mawb.getConsigneeName())).append("</p>");

        String docs = mawb.getSupportingDocs();
        if (docs != null && !docs.isEmpty() && !"[]".equals(docs)) {
            sb.append("<h2>Supporting Documents</h2><table><tr><th>File</th><th>Preview</th></tr>");
            try {
                var list = objectMapper.readValue(docs, List.class);
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) item;
                        String name = map.getOrDefault("name", "").toString();
                        String data = map.getOrDefault("data", "").toString();
                        sb.append("<tr><td>").append(xmlEscape(name)).append("</td>");
                        if (data != null && !data.isEmpty()) {
                            sb.append("<td><img src=\"").append(xmlEscape(data)).append("\"/></td>");
                        } else {
                            sb.append("<td>No preview</td>");
                        }
                        sb.append("</tr>");
                    }
                }
            } catch (Exception ignored) {}
            sb.append("</table>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
