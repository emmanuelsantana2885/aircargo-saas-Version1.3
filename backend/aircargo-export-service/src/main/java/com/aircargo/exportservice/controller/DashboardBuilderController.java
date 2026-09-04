package com.aircargo.exportservice.controller;

import com.aircargo.exportservice.dto.ReportConfigDTO;
import com.aircargo.exportservice.dto.EvaluateResult;
import com.aircargo.exportservice.dto.FieldDefDTO;
import com.aircargo.exportservice.dto.PivotConfig;
import com.aircargo.exportservice.dto.PivotResult;
import com.aircargo.exportservice.entity.DashboardReportEntity;
import com.aircargo.exportservice.repository.DashboardReportRepository;
import com.aircargo.exportservice.service.DashboardBuilderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Dashboard Builder: catálogo de campos, CRUD de reportes y evaluación
 * del reporte calculable (estilo hoja de cálculo).
 */
@RestController
@RequestMapping("/api/dashboard-builder")
public class DashboardBuilderController {

    private final DashboardBuilderService service;
    private final DashboardReportRepository repo;
    private final ObjectMapper objectMapper;

    public DashboardBuilderController(DashboardBuilderService service,
                                      DashboardReportRepository repo,
                                      ObjectMapper objectMapper) {
        this.service = service;
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/fields")
    public ResponseEntity<List<FieldDefDTO>> fields() {
        return ResponseEntity.ok(service.fields());
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateResult> evaluate(@RequestBody ReportConfigDTO cfg) {
        return ResponseEntity.ok(service.evaluate(cfg));
    }

    @PostMapping("/pivot")
    public ResponseEntity<PivotResult> pivot(@RequestBody PivotConfig cfg) {
        return ResponseEntity.ok(service.pivot(cfg));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<Map<String, Object>>> list(@RequestParam(required = false) UUID userId) {
        List<DashboardReportEntity> items;
        if (userId != null) items = repo.findByUserIdOrderByUpdatedAtDesc(userId);
        else items = repo.findBySharedTrueOrderByUpdatedAtDesc();
        return ResponseEntity.ok(items.stream().map(this::toMap).toList());
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id) {
        return repo.findById(id)
                .map(this::toMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reports")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        DashboardReportEntity e = new DashboardReportEntity();
        e.setUserId(uuid(body.get("userId")));
        String name = str(body.get("name"));
        if (e.getUserId() == null || name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        e.setName(name);
        e.setShared(bool(body.get("shared")));
        e.setFieldSources(json(body.get("fieldSources")));
        e.setFormulas(json(body.get("formulas")));
        e.setScenario(json(body.get("scenario")));
        e.setGrouping(json(Collections.singletonMap("dimension", body.getOrDefault("dimension", ""))));
        e.setChartConfig(json(body.get("chartConfig")));
        return ResponseEntity.ok(toMap(repo.save(e)));
    }

    @PutMapping("/reports/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return repo.findById(id).map(existing -> {
            if (body.containsKey("name")) existing.setName(str(body.get("name")));
            if (body.containsKey("shared")) existing.setShared(bool(body.get("shared")));
            if (body.containsKey("fieldSources")) existing.setFieldSources(json(body.get("fieldSources")));
            if (body.containsKey("formulas")) existing.setFormulas(json(body.get("formulas")));
            if (body.containsKey("scenario")) existing.setScenario(json(body.get("scenario")));
            if (body.containsKey("dimension")) existing.setGrouping(json(Collections.singletonMap("dimension", body.getOrDefault("dimension", ""))));
            if (body.containsKey("chartConfig")) existing.setChartConfig(json(body.get("chartConfig")));
            return ResponseEntity.ok(toMap(repo.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam(required = false) UUID userId) {
        if (userId != null) repo.deleteByIdAndUserId(id, userId);
        else repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toMap(DashboardReportEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("name", r.getName());
        m.put("fieldSources", json(r.getFieldSources()));
        m.put("formulas", json(r.getFormulas()));
        m.put("scenario", json(r.getScenario()));
        Map<String, Object> g = asMap(r.getGrouping());
        m.put("dimension", g.getOrDefault("dimension", ""));
        m.put("chartConfig", json(r.getChartConfig()));
        m.put("shared", r.isShared());
        m.put("createdAt", r.getCreatedAt());
        m.put("updatedAt", r.getUpdatedAt());
        return m;
    }

    private Object json(String s) {
        if (s == null || s.isBlank()) return new LinkedHashMap<>();
        try { return objectMapper.readValue(s, Object.class); } catch (Exception e) { return s; }
    }

    private String json(Object o) {
        if (o == null) return "{}";
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(String s) {
        Object v = json(s);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }

    private boolean bool(Object o) { return Boolean.TRUE.equals(o); }

    private UUID uuid(Object o) {
        if (o == null) return null;
        try { return UUID.fromString(String.valueOf(o)); } catch (IllegalArgumentException e) { return null; }
    }
}