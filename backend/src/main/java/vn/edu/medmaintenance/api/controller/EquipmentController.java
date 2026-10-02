package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.EquipmentResponse;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.api.exception.ResourceNotFoundException;
import vn.edu.medmaintenance.api.mapper.EquipmentMapper;
import vn.edu.medmaintenance.persistence.entity.Equipment;
import vn.edu.medmaintenance.persistence.repository.EquipmentRepository;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.security.principal.CurrentUser;
import vn.edu.medmaintenance.service.BusinessRuleException;

@RestController
@RequestMapping("/api/equipment")
public class EquipmentController {
    private static final Set<String> SORT_FIELDS = Set.of("equipmentCode", "name", "model", "id");
    private final EquipmentRepository equipment;
    private final CurrentUser currentUser;

    public EquipmentController(EquipmentRepository equipment, CurrentUser currentUser) {
        this.equipment = equipment;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<EquipmentResponse> list(@Valid @ModelAttribute PageQuery query,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        if (departmentId != null) PageRequests.requirePositive(departmentId, "departmentId");
        Pageable pageable = PageRequests.create(query, SORT_FIELDS, "equipmentCode", Sort.Direction.ASC);
        Page<Equipment> result;
        var viewer = currentUser.get();
        if (viewer.role() == UserRole.KHOA_PHONG) {
            if (viewer.departmentId() == null
                    || (departmentId != null && !departmentId.equals(viewer.departmentId())))
                throw new BusinessRuleException(HttpStatus.FORBIDDEN, "DEPARTMENT_SCOPE_VIOLATION",
                        "Equipment is outside department scope");
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            result = equipment.searchVisible(viewer.role() == UserRole.KHOA_PHONG
                    ? viewer.departmentId() : departmentId, active,
                    viewer.role() == UserRole.KHOA_PHONG, pattern, pageable);
        } else if (viewer.role() == UserRole.KHOA_PHONG) {
            result = equipment.findVisibleForDepartment(viewer.departmentId(), active, pageable);
        } else if (departmentId != null && active != null) {
            result = equipment.findByDepartment_IdAndActive(departmentId, active, pageable);
        } else if (departmentId != null) {
            result = equipment.findByDepartment_Id(departmentId, pageable);
        } else if (active != null) {
            result = equipment.findByActive(active, pageable);
        } else {
            result = equipment.findAllBy(pageable);
        }
        return viewer.role() == UserRole.KHOA_PHONG
                ? PageResponse.from(result, row -> EquipmentMapper.toDepartmentResponse(
                        row, viewer.departmentId()))
                : PageResponse.from(result, EquipmentMapper::toResponse);
    }

    @GetMapping("/{id}")
    public EquipmentResponse detail(@PathVariable Long id) {
        PageRequests.requirePositive(id, "id");
        var viewer = currentUser.get();
        if (viewer.role() == UserRole.KHOA_PHONG) {
            var visible = viewer.departmentId() == null ? java.util.Optional
                    .<Equipment>empty() : equipment.findVisibleForDepartmentById(id, viewer.departmentId());
            if (visible.isEmpty()) {
                if (!equipment.existsById(id)) throw new ResourceNotFoundException("Equipment");
                throw new BusinessRuleException(HttpStatus.FORBIDDEN, "DEPARTMENT_SCOPE_VIOLATION",
                        "Equipment is outside department scope");
            }
            return EquipmentMapper.toDepartmentResponse(visible.orElseThrow(), viewer.departmentId());
        }
        return equipment.findWithDepartmentById(id).map(EquipmentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Equipment"));
    }
}
