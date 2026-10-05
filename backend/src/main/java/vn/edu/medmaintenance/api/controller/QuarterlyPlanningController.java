package vn.edu.medmaintenance.api.controller;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.service.*;
import vn.edu.medmaintenance.api.dto.request.QuarterlyPlanRequest;
import vn.edu.medmaintenance.api.dto.response.PlanCommandResponse;
@RestController @RequestMapping("/api")
public class QuarterlyPlanningController {
 private final QuarterlyPlanningService service;
 public QuarterlyPlanningController(QuarterlyPlanningService service){this.service=service;}
 @GetMapping("/maintenance-plans/preview") public Map<String,Object> preview(@RequestParam int year,@RequestParam MaintenanceQuarter quarter){return service.preview(year,quarter);}
 @PostMapping("/maintenance-plans") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
 public PlanCommandResponse create(@Valid @RequestBody QuarterlyPlanRequest input){return service.create(input);}
 @GetMapping("/companies") public List<Map<String,Object>> companies(){return service.companies();}
 @GetMapping("/providers/{id}/equipment") public List<Map<String,Object>> providerEquipment(@PathVariable long id){return service.providerEquipment(id);}
 @GetMapping("/equipment-catalog") public List<Map<String,Object>> equipment(){return service.equipment();}
 @GetMapping("/equipment/{id}/quarterly-detail") public Map<String,Object> equipment(@PathVariable long id){return service.equipment(id);}
}
