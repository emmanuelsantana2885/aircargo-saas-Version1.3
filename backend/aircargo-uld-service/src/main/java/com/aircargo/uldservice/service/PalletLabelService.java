package com.aircargo.uldservice.service;

import com.aircargo.common.label.LabelRenderer;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.dto.LabelTemplateDTO;
import com.aircargo.common.dto.LabelPrintRequest;
import com.aircargo.uldservice.entity.Uld;
import com.aircargo.uldservice.entity.UldAwb;
import com.aircargo.uldservice.repository.UldAwbRepository;
import com.aircargo.uldservice.repository.UldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PalletLabelService {

    private final UldRepository uldRepository;
    private final UldAwbRepository uldAwbRepository;
    private final MawbClient mawbClient;
    private final LabelRenderer renderer;

    public PalletLabelService(UldRepository uldRepository,
                              UldAwbRepository uldAwbRepository,
                              MawbClient mawbClient,
                              LabelRenderer renderer) {
        this.uldRepository = uldRepository;
        this.uldAwbRepository = uldAwbRepository;
        this.mawbClient = mawbClient;
        this.renderer = renderer;
    }

    public LabelRenderer.LabelSpec resolveSpec(UUID templateId) {
        LabelTemplateDTO dto;
        if (templateId != null) {
            dto = mawbClient.getLabelTemplateById(templateId);
            if (dto == null) throw new IllegalArgumentException("Plantilla PALLET no encontrada: " + templateId);
        } else {
            List<LabelTemplateDTO> templates = mawbClient.getLabelTemplates("PALLET");
            if (templates == null || templates.isEmpty()) {
                throw new IllegalArgumentException("No hay plantilla de etiqueta PALLET configurada");
            }
            dto = templates.stream()
                    .filter(t -> Boolean.TRUE.equals(t.getIsDefault()))
                    .findFirst()
                    .orElse(templates.get(0));
        }
        LabelRenderer.LabelSpec spec = new LabelRenderer.LabelSpec();
        spec.widthInches = dto.getWidthInches().doubleValue();
        spec.heightInches = dto.getHeightInches().doubleValue();
        spec.orientation = dto.getOrientation() != null ? dto.getOrientation() : "HORIZONTAL";
        spec.dpi = dto.getDpi() != null ? dto.getDpi() : 203;
        spec.configJson = dto.getConfigJson();
        return spec;
    }

    @Transactional(readOnly = true)
    public byte[] renderPdf(LabelPrintRequest request) {
        LabelRenderer.LabelSpec spec = resolveSpec(request.getTemplateId());
        try {
            return renderer.renderPdf(spec, buildDataList(request), request.getQuantity() != null ? request.getQuantity() : 1);
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de pallet labels: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public String renderZpl(LabelPrintRequest request) {
        LabelRenderer.LabelSpec spec = resolveSpec(request.getTemplateId());
        return renderer.renderZpl(spec, buildDataList(request), request.getQuantity() != null ? request.getQuantity() : 1);
    }

    private List<Map<String, String>> buildDataList(LabelPrintRequest request) {
        List<Map<String, String>> dataList = new ArrayList<>();
        if (request.getIds() == null) return dataList;
        for (UUID id : request.getIds()) {
            Uld uld = uldRepository.findById(id).orElse(null);
            if (uld == null) continue;
            Map<String, String> data = new HashMap<>();
            data.put("ULD_NUMBER", nvl(uld.getUldNumber()));
            data.put("ULD_TYPE", uld.getUldType() != null ? uld.getUldType() : "");
            data.put("POSITION", nvl(uld.getPosition()));
            data.put("CONFIG", nvl(uld.getConfig()));
            data.put("SEAL", nvl(uld.getSealNumber()));
            data.put("STATUS", uld.getStatus() != null ? uld.getStatus().name() : "");
            data.put("GROSS_LBS", fmt(uld.getGrossWeightLbs()));
            data.put("TARE_LBS", fmt(uld.getTareLbs()));
            data.put("NET_LBS", fmt(uld.getNetWeightLbs()));
            data.put("GROSS_KG", fmt(uld.getGrossWeightKg()));
            data.put("TARE_KG", fmt(uld.getTareKg()));
            data.put("NET_KG", fmt(uld.getNetWeightKg()));

            List<UldAwb> awbs = uldAwbRepository.findByUldId(id);
            int totalPieces = awbs.stream().mapToInt(a -> a.getPieces() != null ? a.getPieces() : 0).sum();
            data.put("PIECES", String.valueOf(totalPieces));
            data.put("MAWBS_COUNT", String.valueOf(awbs.size()));

            if (request.getOverrides() != null) {
                Map<String, String> ov = request.getOverrides().get(id.toString());
                if (ov != null) data.putAll(ov);
            }
            dataList.add(data);
        }
        return dataList;
    }

    private static String fmt(java.math.BigDecimal v) {
        return v != null ? v.stripTrailingZeros().toPlainString() : "";
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
