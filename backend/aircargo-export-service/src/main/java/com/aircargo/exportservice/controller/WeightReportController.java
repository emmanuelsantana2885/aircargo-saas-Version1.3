package com.aircargo.exportservice.controller;

import com.aircargo.exportservice.dto.WeightReportRow;
import com.aircargo.exportservice.service.WeightReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/bi")
public class WeightReportController {

    private final WeightReportService weightReportService;

    public WeightReportController(WeightReportService weightReportService) {
        this.weightReportService = weightReportService;
    }

    @GetMapping("/weight-report")
    public ResponseEntity<List<WeightReportRow>> getWeightReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID flightId,
            @RequestParam(required = false) String commodityType,
            @RequestParam(required = false) String awbNumber,
            @RequestParam(required = false) String shipperName,
            @RequestParam(required = false) String consigneeName,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String hawbNumber) {

        List<WeightReportRow> rows = weightReportService.getWeightReport(
            dateFrom, dateTo, flightId, commodityType,
            awbNumber, shipperName, consigneeName, destination, hawbNumber);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/weight-summary")
    public ResponseEntity<Map<String, Object>> getWeightSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) UUID flightId,
            @RequestParam(required = false) String commodityType,
            @RequestParam(required = false) String awbNumber,
            @RequestParam(required = false) String shipperName,
            @RequestParam(required = false) String consigneeName,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String hawbNumber) {

        Map<String, Object> summary = weightReportService.getWeightSummary(
            dateFrom, dateTo, flightId, commodityType,
            awbNumber, shipperName, consigneeName, destination, hawbNumber);
        return ResponseEntity.ok(summary);
    }
}
