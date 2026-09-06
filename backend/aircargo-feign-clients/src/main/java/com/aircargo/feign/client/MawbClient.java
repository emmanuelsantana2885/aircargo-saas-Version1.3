package com.aircargo.feign.client;

import com.aircargo.feign.dto.MawbDTO;
import com.aircargo.feign.dto.HawbDTO;
import com.aircargo.feign.dto.LabelTemplateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "mawb-service", url = "${mawb-service.url:http://localhost:9095}")
public interface MawbClient {

    @GetMapping("/api/mawbs/{id}")
    MawbDTO getMawbById(@PathVariable UUID id);

    @GetMapping("/api/mawbs/awb/{awbNumber}")
    MawbDTO getMawbByAwbNumber(@PathVariable String awbNumber);

    @GetMapping("/api/mawbs/flight/{flightId}")
    List<MawbDTO> getMawbsByFlight(@PathVariable UUID flightId);

    @GetMapping("/api/hawbs/mawb/{mawbId}")
    List<HawbDTO> getHawbsByMawb(@PathVariable UUID mawbId);

    @GetMapping("/api/hawbs/{id}")
    HawbDTO getHawbById(@PathVariable UUID id);

    @PostMapping("/api/mawbs")
    MawbDTO createMawb(@RequestBody MawbDTO dto);

    @PutMapping("/api/mawbs/{id}")
    MawbDTO updateMawb(@PathVariable UUID id, @RequestBody MawbDTO dto);

    @PatchMapping("/api/mawbs/{id}/status")
    MawbDTO updateMawbStatus(@PathVariable UUID id, @RequestBody String status);

    @GetMapping("/api/label-templates")
    List<LabelTemplateDTO> getLabelTemplates(@RequestParam("type") String type);

    @GetMapping("/api/label-templates/{id}")
    LabelTemplateDTO getLabelTemplateById(@PathVariable UUID id);
}
