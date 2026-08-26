package com.aircargo.loadplanningservice.controller;

import com.aircargo.common.util.TextUtil;
import com.aircargo.feign.dto.UldAwbDTO;
import com.aircargo.feign.dto.UldDTO;
import com.aircargo.loadplanningservice.dto.LoadPlanningDTO;
import com.aircargo.loadplanningservice.dto.LoadPlanningUldDTO;
import com.aircargo.loadplanningservice.service.LoadPlanningExportService;
import com.aircargo.loadplanningservice.service.LoadPlanningService;
import com.aircargo.loadplanningservice.service.RampManifestParserService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/load-planning")
public class LoadPlanningController {

    private final LoadPlanningService loadPlanningService;
    private final RampManifestParserService manifestParserService;
    private final LoadPlanningExportService exportService;

    public LoadPlanningController(LoadPlanningService loadPlanningService,
                                   RampManifestParserService manifestParserService,
                                   LoadPlanningExportService exportService) {
        this.loadPlanningService = loadPlanningService;
        this.manifestParserService = manifestParserService;
        this.exportService = exportService;
    }

    @PostMapping("/flight/{flightId}/close")
    public ResponseEntity<?> closeLoadPlan(@PathVariable java.util.UUID flightId) {
        try {
            LoadPlanningDTO result = loadPlanningService.closeLoadPlan(flightId);
            return ResponseEntity.ok(result);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping("/flight/{flightId}")
    public ResponseEntity<?> getLoadPlanningByFlight(@PathVariable java.util.UUID flightId) {
        return loadPlanningService.getByFlightId(flightId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/flight/{flightId}/upload-manifest")
    public ResponseEntity<?> uploadRampManifest(@PathVariable java.util.UUID flightId,
                                                 @RequestParam("airlineId") java.util.UUID airlineId,
                                                 @RequestParam("file") MultipartFile file) {
        try {
            List<UldDTO> uldsExtraidos = manifestParserService.parseExcelToNativeUld(file, flightId, airlineId);
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", String.format("Exito: Se inyectaron %d ULDs nativos al plan de vuelo.", uldsExtraidos.size())
            ));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping("/flight/{flightId}/export-manifest")
    public ResponseEntity<Resource> downloadRampManifest(@PathVariable java.util.UUID flightId) {
        try {
            ByteArrayInputStream in = exportService.exportFlightLoadPlan(flightId);
            InputStreamResource resource = new InputStreamResource(in);

            String filename = String.format("LOAD_PLAN_FLIGHT_%s.xlsx", flightId.toString().substring(0, 8));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);

        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/flight/{flightId}/pallet-sheets")
    public ResponseEntity<Resource> downloadPalletSheets(@PathVariable java.util.UUID flightId) {
        try {
            Optional<LoadPlanningDTO> opt = loadPlanningService.getByFlightId(flightId);
            if (opt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            LoadPlanningDTO plan = opt.get();
            String html = buildPalletSheetsHtml(plan);
            byte[] pdf = generatePdf(html);

            String flightNum = plan.getFlightNumber() != null ? plan.getFlightNumber().replaceAll("[^a-zA-Z0-9]", "_") : flightId.toString().substring(0, 8);
            String filename = String.format("PALLET_SHEETS_UPS-%s.pdf", flightNum);

            ByteArrayInputStream in = new ByteArrayInputStream(pdf);
            InputStreamResource resource = new InputStreamResource(in);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private byte[] generatePdf(String html) throws Exception {
        try (java.io.ByteArrayOutputStream os = new java.io.ByteArrayOutputStream()) {
            com.openhtmltopdf.pdfboxout.PdfRendererBuilder builder = new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private String buildPalletSheetsHtml(LoadPlanningDTO plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/><title>Pallet Sheets</title>");
        sb.append("<style>");
        sb.append("@page { margin: 10mm 12mm; size: letter portrait; }");
        sb.append("body { font-family: Arial, Helvetica, sans-serif; font-size: 8pt; color: #000; margin: 0; padding: 0; }");
        sb.append(".page { page-break-after: always; padding: 0; }");
        sb.append(".page:last-child { page-break-after: auto; }");
        // Header
        sb.append(".hdr-company { font-size: 16pt; font-weight: bold; text-align: center; letter-spacing: 3pt; margin: 0; }");
        sb.append(".hdr-title { font-size: 12pt; font-weight: bold; text-align: center; text-transform: uppercase; margin: 1pt 0; letter-spacing: 1pt; }");
        sb.append(".hdr-subtitle { font-size: 9pt; text-align: center; text-transform: uppercase; margin: 0 0 6pt 0; letter-spacing: 0.5pt; }");
        // Info grid
        sb.append(".info-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 0; border: 1.5px solid #000; margin-bottom: 4pt; }");
        sb.append(".info-cell { border: 0.5px solid #000; padding: 3pt 5pt; }");
        sb.append(".info-label { font-size: 6.5pt; color: #555; text-transform: uppercase; margin-bottom: 1pt; }");
        sb.append(".info-value { font-size: 9pt; font-weight: bold; min-height: 12pt; }");
        // AWB table
        sb.append("table.awb { width: 100%; border-collapse: collapse; border: 1.5px solid #000; margin-bottom: 4pt; }");
        sb.append("table.awb th { background: #1a1a2e; color: #fff; font-size: 7pt; padding: 3pt 4pt; text-align: left; text-transform: uppercase; }");
        sb.append("table.awb th.c { text-align: center; }");
        sb.append("table.awb td { padding: 3pt 4pt; border-bottom: 0.5px solid #ccc; font-size: 8pt; }");
        sb.append("table.awb td.c { text-align: center; }");
        sb.append("table.awb td.r { text-align: right; }");
        sb.append("table.awb tr.total td { font-weight: bold; border-top: 1.5px solid #000; border-bottom: none; background: #e8e8e8; }");
        // Slave pallet
        sb.append(".slave-grid { display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 0; border: 1.5px solid #000; margin-bottom: 4pt; }");
        sb.append(".slave-cell { border: 0.5px solid #000; padding: 3pt 5pt; }");
        sb.append(".slave-label { font-size: 6.5pt; color: #555; text-transform: uppercase; }");
        sb.append(".slave-value { font-size: 9pt; font-weight: bold; min-height: 12pt; }");
        // Notes + footer
        sb.append(".notes { border: 1.5px solid #000; padding: 4pt 5pt; min-height: 28pt; margin-bottom: 4pt; }");
        sb.append(".notes-title { font-size: 7pt; text-transform: uppercase; font-weight: bold; margin-bottom: 2pt; }");
        sb.append(".footer-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0; border: 1.5px solid #000; }");
        sb.append(".footer-cell { border: 0.5px solid #000; padding: 3pt 5pt; }");
        sb.append(".footer-label { font-size: 6.5pt; color: #555; text-transform: uppercase; }");
        sb.append(".footer-value { font-size: 9pt; font-weight: bold; min-height: 12pt; }");
        sb.append("</style></head><body>");

        List<LoadPlanningUldDTO> ulds = plan.getUlds();
        if (ulds == null || ulds.isEmpty()) {
            sb.append("<p style='text-align:center;margin-top:40pt;'>No hay ULDs asignados a este vuelo.</p>");
        } else {
            for (int i = 0; i < ulds.size(); i++) {
                LoadPlanningUldDTO uld = ulds.get(i);
                if (i < ulds.size() - 1) sb.append("<div class='page'>");
                else sb.append("<div>");

                // ── Header ──
                sb.append("<div class='hdr-company'>UPS AIR CARGO</div>");
                sb.append("<div class='hdr-title'>UNIT LOAD DEVICE</div>");
                sb.append("<div class='hdr-title'>I.D. TAG AND MANIFEST</div>");
                sb.append("<div class='hdr-subtitle'>UNIDAD DE CARGA &#8212; TARJETA DE IDENTIFICACION Y MANIFESTO</div>");

                // ── Flight info grid ──
                String origin = xmlEscape(plan.getOrigin() != null ? plan.getOrigin() : "");
                String dest = xmlEscape(plan.getDestination() != null ? plan.getDestination() : "");
                String flightNum = xmlEscape(plan.getFlightNumber() != null ? plan.getFlightNumber() : "");
                String dateStr = plan.getFlightDate() != null ? xmlEscape(plan.getFlightDate().toString()) : "";
                String acReg = xmlEscape(plan.getAircraftReg() != null ? plan.getAircraftReg() : "");
                String uldNum = xmlEscape(uld.getUldNumber() != null ? uld.getUldNumber() : "");
                String uldType = xmlEscape(uld.getUldType() != null ? uld.getUldType() : "");
                String position = xmlEscape(uld.getPosition() != null ? uld.getPosition() : "");
                String tare = uld.getTareLbs() != null ? uld.getTareLbs().stripTrailingZeros().toPlainString() : "";

                sb.append("<div class='info-grid'>");
                // Row 1
                sb.append("<div class='info-cell'><div class='info-label'>ORIGIN STATION / ESTACION DE ORIGEN</div><div class='info-value'>").append(origin).append("</div></div>");
                sb.append("<div class='info-cell'><div class='info-label'>FLIGHT NUMBER / VUELO NR.</div><div class='info-value'>").append(flightNum).append("</div></div>");
                sb.append("<div class='info-cell'><div class='info-label'>DATE / FECHA</div><div class='info-value'>").append(dateStr).append("</div></div>");
                // Row 2
                sb.append("<div class='info-cell'><div class='info-label'>DESTINATION STATION / ESTACION DE DESTINO</div><div class='info-value'>").append(dest).append("</div></div>");
                sb.append("<div class='info-cell'><div class='info-label'>CONFIGURACION</div><div class='info-value'>").append(uldType).append("</div></div>");
                sb.append("<div class='info-cell' style='display:flex;gap:8pt;'><div style='flex:2'><div class='info-label'>NUMERO DEL CONTENEDOR</div><div class='info-value'>").append(uldNum).append("</div></div><div style='flex:1'><div class='info-label'>TARA (LBS)</div><div class='info-value'>").append(tare).append("</div></div></div>");
                // Row 3
                sb.append("<div class='info-cell'><div class='info-label'>POSICION DEL PALLET</div><div class='info-value'>").append(position).append("</div></div>");
                sb.append("<div class='info-cell'><div class='info-label'>STATUS</div><div class='info-value'>").append(xmlEscape(uld.getStatus() != null ? uld.getStatus() : "OPEN")).append("</div></div>");
                sb.append("<div class='info-cell'><div class='info-label'>SEAL NO. / SELLO NO.</div><div class='info-value'>").append(xmlEscape(uld.getSealNumber() != null ? uld.getSealNumber() : "")).append("</div></div>");
                sb.append("</div>");

                // ── AWB table ──
                List<UldAwbDTO> awbs = uld.getAwbs();
                sb.append("<table class='awb'>");
                sb.append("<thead><tr>");
                sb.append("<th style='width:5%'>#</th>");
                sb.append("<th style='width:25%'>COMPLETE AWB NUMBER / NUMERO AWB COMPLETO</th>");
                sb.append("<th class='c' style='width:10%'>PCS / PIEZAS</th>");
                sb.append("<th class='r' style='width:15%'>WGT (LBS) / PESO (LBS)</th>");
                sb.append("<th style='width:25%'>DESCRIPCION</th>");
                sb.append("<th style='width:10%'>DESTINO</th>");
                sb.append("</tr></thead><tbody>");

                int totalPieces = 0;
                if (awbs != null && !awbs.isEmpty()) {
                    int idx = 1;
                    for (UldAwbDTO awb : awbs) {
                        int pcs = awb.getPieces() != null ? awb.getPieces() : 0;
                        totalPieces += pcs;
                        sb.append("<tr>");
                        sb.append("<td class='c'>").append(idx++).append("</td>");
                        sb.append("<td>").append(xmlEscape(awb.getMawbLabel() != null ? awb.getMawbLabel() : "")).append("</td>");
                        sb.append("<td class='c'>").append(pcs).append("</td>");
                        sb.append("<td class='r'>").append(pcs).append("</td>");
                        sb.append("<td>").append(xmlEscape(awb.getDescription() != null ? awb.getDescription() : "DRY CARGO")).append("</td>");
                        sb.append("<td>").append(xmlEscape(awb.getDestination() != null ? awb.getDestination() : "")).append("</td>");
                        sb.append("</tr>");
                    }
                } else {
                    sb.append("<tr><td colspan='6' style='text-align:center;color:#999;'>No MAWBs assigned</td></tr>");
                }
                // Total row
                String grossStr = uld.getGrossWeightLbs() != null ? uld.getGrossWeightLbs().stripTrailingZeros().toPlainString() : "0";
                String netStr = uld.getNetWeightLbs() != null ? uld.getNetWeightLbs().stripTrailingZeros().toPlainString() : "0";
                sb.append("<tr class='total'>");
                sb.append("<td colspan='2' style='text-align:right;'>TOTAL</td>");
                sb.append("<td class='c'>").append(totalPieces).append("</td>");
                sb.append("<td class='r'>").append(grossStr).append("</td>");
                sb.append("<td colspan='2'>Gross: ").append(grossStr).append(" &nbsp; Tare: ").append(tare).append(" &nbsp; Net: ").append(netStr).append("</td>");
                sb.append("</tr>");
                sb.append("</tbody></table>");

                // ── Slave pallet section ──
                sb.append("<div class='slave-grid'>");
                sb.append("<div class='slave-cell'><div class='slave-label'>NUMERO DEL SLAVE PALLE</div><div class='slave-value'></div></div>");
                sb.append("<div class='slave-cell'><div class='slave-label'>PESO BRUTO</div><div class='slave-value'></div></div>");
                sb.append("<div class='slave-cell'><div class='slave-label'>TARA SLAVE PALLE</div><div class='slave-value'></div></div>");
                sb.append("<div class='slave-cell'><div class='slave-label'>PESO NETO</div><div class='slave-value'></div></div>");
                sb.append("</div>");

                // ── Notes ──
                sb.append("<div class='notes'>");
                sb.append("<div class='notes-title'>Notas / Notes:</div>");
                sb.append("</div>");

                // ── Footer ──
                sb.append("<div class='footer-grid'>");
                sb.append("<div class='footer-cell'><div class='footer-label'>POSICION ARMADA POR / Built by:</div><div class='footer-value'></div></div>");
                sb.append("<div class='footer-cell'><div class='footer-label'>SELLO NO. / Seal No.:</div><div class='footer-value'></div></div>");
                sb.append("<div class='footer-cell'><div class='footer-label'>CONFIRMADA CON / Confirmed with:</div><div class='footer-value'></div></div>");
                sb.append("<div class='footer-cell'><div class='footer-label'>HORA / Time:</div><div class='footer-value'></div></div>");
                sb.append("</div>");

                sb.append("</div>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String xmlEscape(String s) {
        return TextUtil.xmlEscape(s);
    }
}
