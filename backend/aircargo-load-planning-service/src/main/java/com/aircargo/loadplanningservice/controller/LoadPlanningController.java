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

            String flightNum = plan.getFlightNumber() != null ? plan.getFlightNumber() : "";
            String airline = plan.getAirlineCode() != null
                    ? plan.getAirlineCode()
                    : (plan.getAirlineName() != null ? plan.getAirlineName() : "");
            String date = plan.getFlightDate() != null
                    ? plan.getFlightDate().toString() : "";
            java.util.function.Function<String, String> sanitize = (String s) -> s.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
            String segFlight = flightNum.isEmpty() ? "" : sanitize.apply(flightNum);
            String segAirline = airline.isEmpty() ? "" : sanitize.apply(airline);
            String segDate = date.isEmpty() ? "" : sanitize.apply(date);
            String stem = String.join("_",
                    java.util.Arrays.stream(new String[]{segAirline, segFlight, segDate})
                            .filter(s -> !s.isEmpty())
                            .toArray(String[]::new));
            if (stem.isEmpty()) stem = flightId.toString().substring(0, 8);
            String filename = String.format("PALLET_SHEETS_%s.pdf", stem);

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
        sb.append("@page { size: letter portrait; margin: 6mm 10mm; }");
        sb.append("body { font-family: Arial, Helvetica, sans-serif; font-size: 9.5pt; color: #000; margin: 0; padding: 0; line-height: 1.3; }");
        sb.append(".page { padding: 0 3pt 3pt 3pt; border: 2px solid #000; page-break-after: always; }");
        sb.append(".page:last-child { page-break-after: auto; }");
        // ── Header: airline name dynamic ──
        sb.append(".hdr { text-align: center; padding: 12pt 0 6pt 0; border-bottom: 2px solid #000; }");
        sb.append(".hdr-airline { font-size: 24pt; font-weight: bold; letter-spacing: 3pt; margin: 0; }");
        sb.append(".hdr-operator { font-size: 11pt; font-weight: bold; letter-spacing: 2pt; margin: 2pt 0 0 0; color: #333; }");
        sb.append(".hdr-sub { font-size: 14pt; font-weight: bold; letter-spacing: 1.5pt; margin: 3pt 0 0 0; text-transform: uppercase; }");
        // ── Info form: single language fields ──
        sb.append("table.info { width: 100%; border-collapse: collapse; border-bottom: 1.5px solid #000; }");
        sb.append("table.info td { padding: 8pt 6pt 6pt 6pt; vertical-align: bottom; }");
        sb.append(".fl { font-size: 8pt; text-transform: uppercase; color: #333; letter-spacing: 0.4pt; display: block; margin-bottom: 3pt; }");
        sb.append(".fv { font-size: 13pt; font-weight: bold; border-bottom: 1.5px solid #000; padding: 4pt 2pt 5pt 2pt; min-width: 70pt; display: block; }");
        // Inline pairs: simple inline text — compatible with openhtmltopdf
        sb.append(".pair-inline { font-size: 13pt; font-weight: bold; }");
        sb.append(".pair-label { font-size: 8pt; text-transform: uppercase; color: #333; margin-right: 3pt; }");
        sb.append(".pair-value { font-size: 13pt; font-weight: bold; border-bottom: 1.5px solid #000; padding: 4pt 2pt 5pt 2pt; }");
        sb.append(".pair-sep { margin: 0 6pt; color: #666; }");
        // ── Weights group (Tare / Gross / Net): dedicated, prominent ──
        sb.append("table.weights { width: 100%; border-collapse: collapse; border: 1.5px solid #000; margin-top: 8pt; }");
        sb.append("table.weights td { width: 33.33%; padding: 6pt 6pt; vertical-align: bottom; border-right: 1px solid #000; }");
        sb.append("table.weights td:last-child { border-right: none; }");
        sb.append("table.weights .fl { font-size: 8pt; text-transform: uppercase; color: #333; display: block; margin-bottom: 3pt; }");
        sb.append("table.weights .fw { font-size: 16pt; font-weight: bold; border-bottom: 1.5px solid #000; padding: 4pt 2pt 5pt 2pt; text-align: center; }");
        sb.append("table.weights .fw.tare { background: #f2f2f2; }");
        // ── AWB table ──
        sb.append("table.awb { width: 100%; border-collapse: collapse; margin-top: 6pt; }");
        sb.append("table.awb th { font-size: 8.5pt; font-weight: bold; padding: 5pt 5pt; text-align: left; border-bottom: 2px solid #000; border-top: 1px solid #000; text-transform: uppercase; }");
        sb.append("table.awb th.c { text-align: center; }");
        sb.append("table.awb th.r { text-align: right; }");
        sb.append("table.awb td { font-size: 10pt; padding: 4pt 5pt 4pt 5pt; border-bottom: 0.5px solid #999; }");
        sb.append("table.awb td.c { text-align: center; }");
        sb.append("table.awb td.r { text-align: right; }");
        sb.append("table.awb tr.total td { font-weight: bold; border-top: 2px solid #000; border-bottom: 2px solid #000; padding-top: 5pt; padding-bottom: 5pt; }");
        // ── Slave pallet ──
        sb.append("table.slave { width: 100%; border-collapse: collapse; border-top: 2px solid #000; margin-top: 10pt; }");
        sb.append("table.slave td { padding: 6pt 5pt 4pt 5pt; vertical-align: bottom; width: 25%; }");
        sb.append("table.slave .fl { font-size: 8pt; text-transform: uppercase; color: #333; display: block; margin-bottom: 3pt; }");
        sb.append("table.slave .fv { font-size: 11pt; font-weight: bold; border-bottom: 1.5px solid #000; padding: 4pt 2pt; min-width: 46pt; display: block; }");
        // ── Notes ──
        sb.append(".notes { border-top: 2px solid #000; padding: 8pt 8pt; min-height: 34pt; }");
        sb.append(".notes-label { font-size: 8pt; text-transform: uppercase; font-weight: bold; color: #333; }");
        // ── Footer ──
        sb.append("table.footer { width: 100%; border-collapse: collapse; border-top: 2px solid #000; }");
        sb.append("table.footer td { padding: 6pt 5pt 4pt 5pt; vertical-align: bottom; width: 25%; }");
        sb.append("table.footer .fl { font-size: 8pt; text-transform: uppercase; color: #333; display: block; margin-bottom: 3pt; }");
        sb.append("table.footer .fv { font-size: 11pt; font-weight: bold; border-bottom: 1.5px solid #000; padding: 4pt 2pt; display: block; }");
        sb.append("</style></head><body>");

        List<LoadPlanningUldDTO> ulds = plan.getUlds();
        if (ulds == null || ulds.isEmpty()) {
            sb.append("<p style='text-align:center;margin-top:40pt;'>No ULDs assigned to this flight.</p>");
        } else {
            for (int i = 0; i < ulds.size(); i++) {
                LoadPlanningUldDTO uld = ulds.get(i);
                if (i < ulds.size() - 1) sb.append("<div class='page'>");
                else sb.append("<div>");

                // ── Header ──
                String airlineName = plan.getAirlineName();
                if (airlineName == null || airlineName.isBlank()) airlineName = "AIR CARGO";
                String airlineCode = plan.getAirlineCode() != null ? plan.getAirlineCode().toUpperCase()
                        : (plan.getAirlineCode() == null ? "" : plan.getAirlineCode());
                String hdrFlight = plan.getFlightNumber() != null ? plan.getFlightNumber() : "";
                String hdrDate = plan.getFlightDate() != null ? plan.getFlightDate().toString() : "";
                sb.append("<div class='hdr'>");
                sb.append("<div class='hdr-airline'>").append(xmlEscape(airlineName)).append("</div>");
                sb.append("<div class='hdr-operator'>").append(xmlEscape(airlineCode)).append(" &#183; ").append(xmlEscape(hdrFlight)).append(" &#183; ").append(xmlEscape(hdrDate)).append("</div>");
                sb.append("<div class='hdr-sub'>Unit Load Device</div>");
                sb.append("<div class='hdr-sub'>I.D. Tag and Manifest</div>");
                sb.append("</div>");

                // ── Flight info form ──
                String o = v(plan.getOrigin());
                String d = v(plan.getDestination());
                String fn = v(plan.getFlightNumber());
                String dt = plan.getFlightDate() != null ? xmlEscape(plan.getFlightDate().toString()) : "";
                String un = v(uld.getUldNumber());
                String ut = v(uld.getUldType());
                String pos = v(uld.getPosition());
                String tare = bd(uld.getTareLbs());
                String seal = v(uld.getSealNumber());
                String sts = v(uld.getStatus());
                String operator = plan.getAirlineCode() != null && !plan.getAirlineCode().isBlank()
                        ? plan.getAirlineCode().toUpperCase() : airlineName;
                String statusSeal = seal != null && !seal.isEmpty() && !seal.isBlank()
                        ? sts + " \u2022 " + seal : sts;
                String builtBy = v(uld.getBuiltBy());
                String confirmedWith = v(uld.getConfirmedWith());
                String completedAt = v(uld.getCompletedAt());

                sb.append("<table class='info'>");
                // Row 1: Origin | Flight | Date
                sb.append("<tr>");
                sb.append("<td style='width:38%'><span class='fl'>Origin Station</span><span class='fv fv-wide'>").append(o).append("</span></td>");
                sb.append("<td style='width:33%'><span class='fl'>Flight Number</span><span class='fv'>").append(fn).append("</span></td>");
                sb.append("<td style='width:29%'><span class='fl'>Date</span><span class='fv'>").append(dt).append("</span></td>");
                sb.append("</tr>");
                // Row 2: Destination | Airline/Operator | ULD Number
                sb.append("<tr>");
                sb.append("<td><span class='fl'>Destination Station</span><span class='fv fv-wide'>").append(d).append("</span></td>");
                sb.append("<td><span class='fl'>Airline / Operator</span><span class='fv'>").append(xmlEscape(operator)).append("</span></td>");
                sb.append("<td><span class='fl'>ULD Number</span><span class='fv'>").append(un).append("</span></td>");
                sb.append("</tr>");
                // Row 3: ULD Type | ULD Position | Status
                sb.append("<tr>");
                sb.append("<td><span class='fl'>ULD Type</span><span class='fv'>").append(ut).append("</span></td>");
                sb.append("<td><span class='fl'>ULD Position</span><span class='fv'>").append(pos).append("</span></td>");
                sb.append("<td><span class='fl'>Status / Seal No.</span><span class='fv'>").append(xmlEscape(statusSeal)).append("</span></td>");
                sb.append("</tr>");
                sb.append("</table>");

                // ── Weights block: Tare / Gross / Net (prominent) ──
                sb.append("<table class='weights'>");
                sb.append("<tr>");
                sb.append("<td><span class='fl'>Tare (Lbs)</span><span class='fw tare'>").append(tare).append("</span></td>");
                sb.append("<td><span class='fl'>Gross (Lbs)</span><span class='fw'>").append(bd(uld.getGrossWeightLbs())).append("</span></td>");
                sb.append("<td><span class='fl'>Net (Lbs)</span><span class='fw'>").append(bd(uld.getNetWeightLbs())).append("</span></td>");
                sb.append("</tr></table>");

                // ── AWB breakdown table ──
                List<UldAwbDTO> awbs = uld.getAwbs();
                int totalPieces = 0;
                sb.append("<table class='awb'>");
                sb.append("<thead><tr>");
                sb.append("<th style='width:5%' class='c'>#</th>");
                sb.append("<th style='width:28%'>AWB Number</th>");
                sb.append("<th style='width:10%' class='c'>Pcs</th>");
                sb.append("<th style='width:12%' class='r'>Wgt (Lbs)</th>");
                sb.append("<th style='width:28%'>Description</th>");
                sb.append("<th style='width:12%'>Destination</th>");
                sb.append("</tr></thead><tbody>");

                if (awbs != null && !awbs.isEmpty()) {
                    int idx = 1;
                    for (UldAwbDTO awb : awbs) {
                        int pcs = awb.getPieces() != null ? awb.getPieces() : 0;
                        totalPieces += pcs;
                        sb.append("<tr>");
                        sb.append("<td class='c'>").append(idx++).append("</td>");
                        sb.append("<td>").append(xmlEscape(nz(awb.getMawbLabel()))).append("</td>");
                        sb.append("<td class='c'>").append(pcs).append("</td>");
                        sb.append("<td class='r'>").append(pcs).append("</td>");
                        sb.append("<td>").append(xmlEscape(nz(awb.getDescription()))).append("</td>");
                        sb.append("<td>").append(xmlEscape(nz(awb.getDestination()))).append("</td>");
                        sb.append("</tr>");
                    }
                } else {
                    sb.append("<tr class='empty'><td colspan='6'>No MAWBs assigned</td></tr>");
                }

                String gross = bd(uld.getGrossWeightLbs());
                String net = bd(uld.getNetWeightLbs());
                sb.append("<tr class='total'>");
                sb.append("<td colspan='2' style='text-align:right;'>Total</td>");
                sb.append("<td class='c'>").append(totalPieces).append("</td>");
                sb.append("<td class='r'>").append(gross).append("</td>");
                sb.append("<td colspan='2' style='font-size:10pt;'>Gross: ").append(gross)
                        .append(" &#160;|&#160; <span style='background:#f2f2f2;padding:1pt 4pt;border:1px solid #000;'>Tare: ").append(tare)
                        .append("</span> &#160;|&#160; Net: ").append(net).append("</td>");
                sb.append("</tr>");
                sb.append("</tbody></table>");

                // ── Notes ──
                sb.append("<div class='notes'><span class='notes-label'>Notes:</span></div>");

                // ── Footer ──
                sb.append("<table class='footer'>");
                sb.append("<tr>");
                sb.append("<td><span class='fl'>Built By:</span><span class='fv'>").append(builtBy).append("</span></td>");
                sb.append("<td><span class='fl'>Completed At:</span><span class='fv'>").append(completedAt).append("</span></td>");
                sb.append("<td><span class='fl'>Confirmed With:</span><span class='fv'>").append(confirmedWith).append("</span></td>");
                sb.append("<td><span class='fl'>Time:</span><span class='fv'>").append(completedAt).append("</span></td>");
                sb.append("</tr></table>");

                sb.append("</div>");
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    private String v(String s) { return s != null ? xmlEscape(s) : ""; }

    private String bd(java.math.BigDecimal val) {
        return val != null ? val.stripTrailingZeros().toPlainString() : "";
    }

    private String nz(String s) { return s != null ? s : ""; }

    private String xmlEscape(String s) {
        return TextUtil.xmlEscape(s);
    }
}
