package vn.edu.medmaintenance.api.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.service.MaintenanceTrackingService;
import vn.edu.medmaintenance.api.dto.request.FinalizeReportRequest;
@RestController @RequestMapping("/api/plans/{planId}")
public class MaintenanceTrackingController {
 private final MaintenanceTrackingService service;
 public MaintenanceTrackingController(MaintenanceTrackingService service){this.service=service;}
 @GetMapping("/tracking") public List<Map<String,Object>> tracking(@PathVariable long planId){return service.tracking(planId);}
 @PostMapping("/complete-maintenance") public Map<String,Object> complete(@PathVariable long planId,@Valid @RequestBody FinalizeReportRequest input){return service.complete(planId,input);}
}
