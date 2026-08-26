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
        sb.append("@page { size: letter portrait; margin: 6mm 10mm; }");
        sb.append("body { font-family: Arial, Helvetica, sans-serif; font-size: 8pt; color: #000; margin: 0; padding: 0; }");
        sb.append(".page { page-break-after: always; padding: 0; border: 2px solid #000; }");
        sb.append(".page:last-child { page-break-after: auto; }");
        // ── Header ──
        sb.append(".hdr { text-align: center; padding: 8pt 0 2pt 0; border-bottom: 2px solid #000; }");
        sb.append(".hdr-en { font-size: 18pt; font-weight: bold; letter-spacing: 2pt; margin: 0; }");
        sb.append(".hdr-sub { font-size: 11pt; font-weight: bold; letter-spacing: 1pt; margin: 1pt 0; }");
        sb.append(".hdr-es { font-size: 9pt; letter-spacing: 0.5pt; margin: 0; text-transform: uppercase; }");
        // ── Info form ──
        sb.append("table.info { width: 100%; border-collapse: collapse; border-bottom: 1.5px solid #000; }");
        sb.append("table.info td { padding: 1pt 0; vertical-align: bottom; }");
        sb.append(".fl { font-size: 6pt; text-transform: uppercase; color: #333; padding-bottom: 0; }");
        sb.append(".fv { font-size: 10pt; font-weight: bold; border-bottom: 1px solid #000; padding: 1pt 2pt 2pt 2pt; min-width: 40pt; }");
        sb.append(".fv-wide { min-width: 120pt; }");
        // ── AWB table ──
        sb.append("table.awb { width: 100%; border-collapse: collapse; }");
        sb.append("table.awb th { font-size: 7pt; font-weight: bold; padding: 2pt 3pt; text-align: left; border-bottom: 1.5px solid #000; border-top: 1px solid #000; text-transform: uppercase; }");
        sb.append("table.awb th.c { text-align: center; }");
        sb.append("table.awb th.r { text-align: right; }");
        sb.append("table.awb td { font-size: 8pt; padding: 2pt 3pt; border-bottom: 0.5px solid #999; }");
        sb.append("table.awb td.c { text-align: center; }");
        sb.append("table.awb td.r { text-align: right; }");
        sb.append("table.awb tr.total td { font-weight: bold; border-top: 1.5px solid #000; border-bottom: 1.5px solid #000; padding-top: 3pt; }");
        sb.append("table.awb tr.empty td { color: #999; text-align: center; font-style: italic; padding: 8pt 0; }");
        // ── Slave pallet ──
        sb.append("table.slave { width: 100%; border-collapse: collapse; border-top: 1.5px solid #000; margin-top: 6pt; }");
        sb.append("table.slave td { padding: 1pt 0; vertical-align: bottom; width: 25%; }");
        sb.append("table.slave .fl { font-size: 6pt; text-transform: uppercase; color: #333; }");
        sb.append("table.slave .fv { font-size: 9pt; font-weight: bold; border-bottom: 1px solid #000; padding: 1pt 2pt 2pt 2pt; min-width: 30pt; }");
        // ── Notes ──
        sb.append(".notes { border-top: 1.5px solid #000; padding: 3pt 4pt; min-height: 22pt; }");
        sb.append(".notes-label { font-size: 6pt; text-transform: uppercase; font-weight: bold; color: #333; }");
        // ── Footer ──
        sb.append("table.footer { width: 100%; border-collapse: collapse; border-top: 1.5px solid #000; }");
        sb.append("table.footer td { padding: 1pt 0; vertical-align: bottom; width: 25%; }");
        sb.append("table.footer .fl { font-size: 6pt; text-transform: uppercase; color: #333; }");
        sb.append("table.footer .fv { font-size: 9pt; font-weight: bold; border-bottom: 1px solid #000; padding: 1pt 2pt 2pt 2pt; }");
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
                sb.append("<div class='hdr'>");
                sb.append("<div class='hdr-en'>UPS AIR CARGO</div>");
                sb.append("<div class='hdr-sub'>UNIT LOAD DEVICE</div>");
                sb.append("<div class='hdr-sub'>I.D. TAG AND MANIFEST</div>");
                sb.append("<div class='hdr-es'>UNIDAD DE CARGA &#8212; TARJETA DE IDENTIFICACION Y MANIFESTO</div>");
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

                sb.append("<table class='info'>");
                // Row 1: Origin | Flight | Date
                sb.append("<tr>");
                sb.append("<td class='fl' style='width:42%'><div class='fl'>ORIGIN STATION</div><div class='fv fv-wide'>").append(o).append("</div><div class='fl'>ESTACION DE ORIGEN</div></td>");
                sb.append("<td class='fl' style='width:32%'><div class='fl'>FLIGHT NUMBER</div><div class='fv'>").append(fn).append("</div><div class='fl'>VUELO NR.</div></td>");
                sb.append("<td class='fl' style='width:26%'><div class='fl'>DATE</div><div class='fv'>").append(dt).append("</div><div class='fl'>FECHA</div></td>");
                sb.append("</tr>");
                // Row 2: Destination | Config | Container + Tare
                sb.append("<tr>");
                sb.append("<td><div class='fl'>DESTINACION STATION</div><div class='fv fv-wide'>").append(d).append("</div><div class='fl'>ESTACION DE DESTINO</div></td>");
                sb.append("<td><div class='fl'>CONFIGURACION</div><div class='fv'>").append(ut).append("</div><div class='fl'>POSICION DEL PALLET</div></td>");
                sb.append("<td><div class='fl'>NUMERO DEL CONTENEDOR</div><div class='fv'>").append(un).append("</div><div class='fl'>TARA (LBS) &nbsp;&nbsp; ").append(tare).append("</div></td>");
                sb.append("</tr>");
                // Row 3: Position | Status | Seal
                sb.append("<tr>");
                sb.append("<td><div class='fl'>POSICION DEL PALLET</div><div class='fv fv-wide'>").append(pos).append("</div></td>");
                sb.append("<td><div class='fl'>STATUS</div><div class='fv'>").append(sts).append("</div></td>");
                sb.append("<td><div class='fl'>SEAL NO. / SELLO NO.</div><div class='fv'>").append(seal).append("</div></td>");
                sb.append("</tr>");
                sb.append("</table>");

                // ── AWB breakdown table ──
                List<UldAwbDTO> awbs = uld.getAwbs();
                int totalPieces = 0;
                sb.append("<table class='awb'>");
                sb.append("<thead><tr>");
                sb.append("<th style='width:5%' class='c'>#</th>");
                sb.append("<th style='width:30%'>COMPLETE AWB NUMBER / NUMERO AWB COMPLETO</th>");
                sb.append("<th style='width:10%' class='c'>PCS / PIEZAS</th>");
                sb.append("<th style='width:15%' class='r'>WGT (LBS) / PESO (LBS)</th>");
                sb.append("<th style='width:25%'>DESCRIPCION</th>");
                sb.append("<th style='width:15%'>DESTINO</th>");
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
                sb.append("<td colspan='2' style='text-align:right;'>TOTAL</td>");
                sb.append("<td class='c'>").append(totalPieces).append("</td>");
                sb.append("<td class='r'>").append(gross).append("</td>");
                sb.append("<td colspan='2'>Gross: ").append(gross).append(" &nbsp; Tare: ").append(tare).append(" &nbsp; Net: ").append(net).append("</td>");
                sb.append("</tr>");
                sb.append("</tbody></table>");

                // ── Slave pallet ──
                sb.append("<table class='slave'>");
                sb.append("<tr>");
                sb.append("<td><div class='fl'>NUMERO DEL SLAVE PALLE</div><div class='fv'></div></td>");
                sb.append("<td><div class='fl'>PESO BRUTO</div><div class='fv'></div></td>");
                sb.append("<td><div class='fl'>TARA SLAVE PALLE</div><div class='fv'></div></td>");
                sb.append("<td><div class='fl'>PESO NETO</div><div class='fv'></div></td>");
                sb.append("</tr></table>");

                // ── Notes ──
                sb.append("<div class='notes'><span class='notes-label'>Notas / Notes:</span></div>");

                // ── Footer ──
                sb.append("<table class='footer'>");
                sb.append("<tr>");
                sb.append("<td><div class='fl'>POSICION ARMADA POR / Built by:</div><div class='fv'></div></td>");
                sb.append("<td><div class='fl'>SELLO NO. / Seal No.:</div><div class='fv'></div></td>");
                sb.append("<td><div class='fl'>CONFIRMADA CON / Confirmed with:</div><div class='fv'></div></td>");
                sb.append("<td><div class='fl'>HORA / Time:</div><div class='fv'></div></td>");
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
