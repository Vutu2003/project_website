package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.*;
import vn.edu.medmaintenance.service.MaintenanceSuggestionService;

@RestController @RequestMapping("/api/maintenance-suggestions") public class MaintenanceSuggestionController {
    private final MaintenanceSuggestionService service;
    public MaintenanceSuggestionController(MaintenanceSuggestionService service) {
        this.service=service;
    }
    @GetMapping public PageResponse<MaintenanceSuggestionResponse> list(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required=false) String search,
            @RequestParam(defaultValue="true") Boolean active,
            @RequestParam(defaultValue="false") boolean includeInactive,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate referenceDate,
            @RequestParam(required=false) Long departmentId,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dueFrom,
            @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dueTo,
            @RequestParam(required=false) String dueStatus,
            @RequestParam(required=false) vn.edu.medmaintenance.persistence.enums.CoverageClassification classification,
            @RequestParam(required=false) Long providerId,@RequestParam(required=false) Boolean notInOpenPlan) {
        if(dueFrom!=null && dueTo!=null && dueTo.isBefore(dueFrom))throw new vn.edu.medmaintenance.api.exception.InvalidParameterException("maintenance", "Khoảng ngày đến hạn không hợp lệ");
        if(dueStatus!=null && !Set.of("NOT_DUE","DUE_SOON","DUE","OVERDUE","NO_SCHEDULE").contains(dueStatus))throw new vn.edu.medmaintenance.api.exception.InvalidParameterException("maintenance", "Trạng thái đến hạn không hợp lệ");
        return service.list(PageRequests.create(query, Set.of("equipmentCode", "id", "nextMaintenanceDueDate"), "equipmentCode", Sort.Direction.ASC), search, includeInactive ? null : active, referenceDate,new MaintenanceSuggestionService.Filters(departmentId,dueFrom,dueTo,dueStatus,classification,providerId,notInOpenPlan));
    }
}
