package vn.edu.medmaintenance.api.controller;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import vn.edu.medmaintenance.service.EquipmentManagementService;
import vn.edu.medmaintenance.api.dto.request.CreateEquipmentRequest;
@RestController @RequestMapping("/api/equipment")
public class EquipmentManagementController {
 private final EquipmentManagementService service;
 public EquipmentManagementController(EquipmentManagementService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@Valid @RequestBody CreateEquipmentRequest input){return service.create(input);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@PathVariable long id){service.remove(id);}
}
