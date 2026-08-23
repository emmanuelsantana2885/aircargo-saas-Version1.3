package com.aircargo.uldservice.controller;

import com.aircargo.common.auth.UserPrincipal;
import com.aircargo.common.dto.PageResponse;
import com.aircargo.uldservice.dto.TransferRequest;
import com.aircargo.uldservice.dto.UldDTO;
import com.aircargo.uldservice.entity.UldStatus;
import com.aircargo.uldservice.service.UldService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ulds")
public class UldController {

    private final UldService uldService;

    public UldController(UldService uldService) {
        this.uldService = uldService;
    }

    @GetMapping
    public List<UldDTO> getAll(
            @RequestParam(required = false) UUID airlineId,
            @RequestParam(required = false) UUID flightId) {
        return uldService.getAll(airlineId, flightId);
    }

    @GetMapping(params = {"page", "size"})
    public PageResponse<UldDTO> getAllPaginated(
            @RequestParam(required = false) UUID airlineId,
            @RequestParam(required = false) UUID flightId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return uldService.getAll(airlineId, flightId, page, size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UldDTO> getById(@PathVariable UUID id) {
        return uldService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UldDTO> create(@Valid @RequestBody UldDTO dto,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          HttpServletRequest request) {
        UldDTO created = uldService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UldDTO> update(@PathVariable UUID id, @Valid @RequestBody UldDTO dto,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          HttpServletRequest request) {
        return uldService.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UldDTO> patch(@PathVariable UUID id, @RequestBody Map<String, Object> fields,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        return uldService.getById(id)
                .map(existing -> {
                    UldDTO dto = new UldDTO();
                    if (fields.containsKey("position")) dto.setPosition((String) fields.get("position"));
                    if (fields.containsKey("uldNumber")) dto.setUldNumber((String) fields.get("uldNumber"));
                    if (fields.containsKey("uldType")) dto.setUldType((String) fields.get("uldType"));
                    if (fields.containsKey("sealNumber")) dto.setSealNumber((String) fields.get("sealNumber"));
                    if (fields.containsKey("status")) dto.setStatus(UldStatus.valueOf((String) fields.get("status")));
                    if (fields.containsKey("config")) dto.setConfig((String) fields.get("config"));
                    if (fields.containsKey("notes")) dto.setNotes((String) fields.get("notes"));
                    if (fields.containsKey("flightId")) {
                        String fid = (String) fields.get("flightId");
                        dto.setFlightId(fid != null && !"null".equals(fid) ? UUID.fromString(fid) : null);
                    }
                    if (fields.containsKey("tareLbs") && fields.get("tareLbs") instanceof Number n) {
                        dto.setTareLbs(new BigDecimal(n.toString()));
                    }
                    if (fields.containsKey("grossWeightLbs") && fields.get("grossWeightLbs") instanceof Number n) {
                        dto.setGrossWeightLbs(new BigDecimal(n.toString()));
                    }
                    return uldService.update(id, dto);
                })
                .flatMap(opt -> opt)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/flight")
    public ResponseEntity<?> assignFlight(@PathVariable UUID id, @RequestBody Map<String, String> body,
                                           @AuthenticationPrincipal UserPrincipal principal,
                                           HttpServletRequest request) {
        String flightIdStr = body.get("flightId");
        try {
            UUID flightId = (flightIdStr == null || flightIdStr.isBlank() || "null".equalsIgnoreCase(flightIdStr))
                    ? null : UUID.fromString(flightIdStr);
            UldDTO updated = uldService.assignFlight(id, flightId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @PostMapping("/{uldId}/transfer")
    public ResponseEntity<?> transferUld(@PathVariable UUID uldId, @Valid @RequestBody TransferRequest transferRequest,
                                          @AuthenticationPrincipal UserPrincipal principal,
                                          HttpServletRequest request) {
        if (transferRequest.getDestinationFlightId() == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "destinationFlightId is required"));
        }
        if (transferRequest.getReason() == null || transferRequest.getReason().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "reason is required for transfer"));
        }
        try {
            UldDTO result = uldService.transferUld(uldId, transferRequest.getDestinationFlightId(), transferRequest.getReason());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        boolean removed = uldService.delete(id);
        if (!removed) return ResponseEntity.notFound().build();
        return ResponseEntity.noContent().build();
    }
}
