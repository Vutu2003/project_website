package vn.edu.medmaintenance.api.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.service.ReportDeliveryService;
import vn.edu.medmaintenance.api.dto.request.*;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
@RestController @RequestMapping("/api")
public class ReportDeliveryController {
 private final ReportDeliveryService service;
 public ReportDeliveryController(ReportDeliveryService service){this.service=service;}
 @GetMapping("/plans/{planId}/report/delivery") public Map<String,Object> delivery(@PathVariable long planId){return service.delivery(planId);}
 @PostMapping("/plans/{planId}/report/send") public Map<String,Object> send(@PathVariable long planId,@Valid @RequestBody FinalizeReportRequest input){return service.send(planId,input);}
 @GetMapping("/received-reports") public PageResponse<Map<String,Object>> received(@Valid @ModelAttribute PageQuery query){return service.received(query.getPage(),query.getSize());}
}
