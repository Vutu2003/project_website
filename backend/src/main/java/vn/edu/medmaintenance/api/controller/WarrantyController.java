package vn.edu.medmaintenance.api.controller;

import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.UpdateWarrantyRequest;
import vn.edu.medmaintenance.api.dto.response.WarrantyResponse;
import vn.edu.medmaintenance.service.WarrantyService;

@RestController
@RequestMapping("/api/equipment/{equipmentId}/warranty")
public class WarrantyController {
    private final WarrantyService service;
    public WarrantyController(WarrantyService service) { this.service=service; }
    @GetMapping
    public WarrantyResponse detail(@PathVariable Long equipmentId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        PageRequests.requirePositive(equipmentId, "equipmentId");
        return service.detail(equipmentId, referenceDate);
    }
    @PutMapping
    public WarrantyResponse update(@PathVariable Long equipmentId, @Valid @RequestBody UpdateWarrantyRequest input) {
        PageRequests.requirePositive(equipmentId, "equipmentId");
        return service.update(equipmentId, input);
    }
}
