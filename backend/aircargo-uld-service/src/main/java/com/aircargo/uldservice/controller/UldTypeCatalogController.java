package com.aircargo.uldservice.controller;

import com.aircargo.uldservice.dto.UldTypeCatalogDTO;
import com.aircargo.uldservice.service.UldTypeCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/uld-type-catalog")
public class UldTypeCatalogController {

    private final UldTypeCatalogService service;

    public UldTypeCatalogController(UldTypeCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<UldTypeCatalogDTO>> getAll(
            @RequestParam(name = "activeOnly", required = false, defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(service.getAll(activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UldTypeCatalogDTO> getById(@PathVariable UUID id) {
        return service.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UldTypeCatalogDTO> create(@RequestBody UldTypeCatalogDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UldTypeCatalogDTO> update(@PathVariable UUID id, @RequestBody UldTypeCatalogDTO dto) {
        return service.update(id, dto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
