package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.dto.request.FinalizeReportRequest;
import vn.edu.medmaintenance.api.dto.request.SaveReportRequest;
import vn.edu.medmaintenance.api.dto.response.ReportResponse;
import vn.edu.medmaintenance.service.MaintenanceReportService;

@RestController
@RequestMapping("/api/plans/{planId}/report")
public class MaintenanceReportController {
    private final MaintenanceReportService reports;

    public MaintenanceReportController(MaintenanceReportService reports) { this.reports = reports; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@PathVariable Long planId, @Valid @RequestBody SaveReportRequest command) {
        return reports.create(planId, command);
    }

    @PutMapping
    public ReportResponse edit(@PathVariable Long planId, @Valid @RequestBody SaveReportRequest command) {
        return reports.edit(planId, command);
    }

    @PostMapping("/finalize")
    public ReportResponse finalizeReport(@PathVariable Long planId,
            @Valid @RequestBody FinalizeReportRequest command) {
        return reports.finalizeReport(planId, command);
    }

    @GetMapping
    public ReportResponse get(@PathVariable Long planId) { return reports.get(planId); }
}
