package vn.edu.medmaintenance.api.controller;

import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.dto.response.DashboardResponse;
import vn.edu.medmaintenance.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard) { this.dashboard = dashboard; }
    @GetMapping
    public DashboardResponse get() { return dashboard.get(); }
}
