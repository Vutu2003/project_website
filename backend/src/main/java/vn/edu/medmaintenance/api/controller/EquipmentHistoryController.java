package vn.edu.medmaintenance.api.controller;

import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.dto.response.EquipmentHistoryResponse;
import vn.edu.medmaintenance.service.EquipmentHistoryService;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentHistoryController {
    private final EquipmentHistoryService history;

    public EquipmentHistoryController(EquipmentHistoryService history) { this.history = history; }

    @GetMapping("/{id}/maintenance-history")
    public EquipmentHistoryResponse get(@PathVariable Long id) { return history.get(id); }
}
