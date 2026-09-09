package com.aircargo.warehouseservice.controller;

import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.service.WarehouseReceiptService;
import com.aircargo.common.audit.AuditService;
import com.aircargo.common.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
public class WarehouseReceiptController {

    private final WarehouseReceiptService receiptService;
    private final com.aircargo.warehouseservice.service.WarehouseService warehouseService;
    private final AuditService auditService;

    public WarehouseReceiptController(WarehouseReceiptService receiptService,
                                      com.aircargo.warehouseservice.service.WarehouseService warehouseService,
                                      AuditService auditService) {
        this.receiptService = receiptService;
        this.warehouseService = warehouseService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<WarehouseReceiptDTO> getAll() {
        return receiptService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseReceiptDTO> getById(@PathVariable UUID id) {
        return receiptService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<WarehouseReceiptDTO> create(@Valid @RequestBody WarehouseReceiptDTO dto,
                                                       @AuthenticationPrincipal UserPrincipal principal,
                                                       HttpServletRequest request) {
        WarehouseReceiptDTO created = warehouseService.emitReceipt(dto, principal, request);
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "RECEIPT_CREATE", "RECEIPT", created.getId().toString(),
                "{\"mawbId\":\"" + (created.getMawbId() != null ? created.getMawbId() : "") + "\"}",
                request.getRemoteAddr()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WarehouseReceiptDTO> update(@PathVariable UUID id,
                                                       @Valid @RequestBody WarehouseReceiptDTO dto,
                                                       @AuthenticationPrincipal UserPrincipal principal,
                                                       HttpServletRequest request) {
        return warehouseService.updateReceipt(id, dto, principal, request)
                .map(updated -> {
                    auditService.log(
                            principal != null ? principal.getUserIdAsUuid() : null,
                            principal != null ? principal.email() : "system",
                            principal != null ? principal.fullName() : "system",
                            "RECEIPT_UPDATE", "RECEIPT", updated.getId().toString(),
                            "{\"mawbId\":\"" + (updated.getMawbId() != null ? updated.getMawbId() : "") + "\"}",
                            request.getRemoteAddr()
                    );
                    return ResponseEntity.ok(updated);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal,
                                        HttpServletRequest request) {
        boolean exists = receiptService.getById(id).isPresent();
        if (!exists) return ResponseEntity.notFound().build();
        receiptService.delete(id);
        auditService.log(
                principal != null ? principal.getUserIdAsUuid() : null,
                principal != null ? principal.email() : "system",
                principal != null ? principal.fullName() : "system",
                "RECEIPT_DELETE", "RECEIPT", id.toString(),
                null,
                request.getRemoteAddr()
        );
        return ResponseEntity.noContent().build();
    }
}