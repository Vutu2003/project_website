package vn.edu.medmaintenance.api.controller;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import vn.edu.medmaintenance.service.MaintenanceAutomationService;
@RestController @RequestMapping("/api")
public class MaintenanceAutomationController {
 private final MaintenanceAutomationService service;
 private final vn.edu.medmaintenance.service.PlanningDecisionService decisions;
 public MaintenanceAutomationController(MaintenanceAutomationService s,vn.edu.medmaintenance.service.PlanningDecisionService d){service=s;decisions=d;}
 @GetMapping("/equipment/{id}/planning-context") public Map<String,Object> preview(@PathVariable Long id,
 @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate referenceDate){
   var result=decisions.preview(id,referenceDate);result.put("schedule",service.schedule(id));return result;
 }
 @GetMapping("/contracts") public List<Map<String,Object>> contracts(@RequestParam(required=false) Long providerId){return service.contracts(providerId);}
 @GetMapping("/contracts/{id}") public Map<String,Object> contract(@PathVariable long id){return service.contract(id);}
 @GetMapping("/contracts/{id}/equipment") public List<Map<String,Object>> equipment(@PathVariable long id){return service.contractEquipment(id);}
 @GetMapping("/providers/{id}/detail") public Map<String,Object> provider(@PathVariable long id){return service.provider(id);}
 @GetMapping("/equipment/{id}/schedule") public Map<String,Object> schedule(@PathVariable long id){return service.schedule(id);}
 @PutMapping("/equipment/{id}/schedule") public Map<String,Object> configure(@PathVariable long id,@Valid @RequestBody MaintenanceAutomationService.ScheduleInput input){return service.configure(id,input);}
}
