package vn.edu.medmaintenance.api.controller;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.medmaintenance.api.dto.response.DepartmentResponse;
import vn.edu.medmaintenance.api.dto.response.ServiceProviderResponse;
import vn.edu.medmaintenance.api.mapper.DepartmentMapper;
import vn.edu.medmaintenance.api.mapper.ServiceProviderMapper;
import vn.edu.medmaintenance.persistence.repository.DepartmentRepository;
import vn.edu.medmaintenance.persistence.repository.ServiceProviderRepository;

@RestController
@RequestMapping("/api")
public class ReferenceController {
    private final DepartmentRepository departments;
    private final ServiceProviderRepository providers;

    public ReferenceController(DepartmentRepository departments, ServiceProviderRepository providers) {
        this.departments = departments;
        this.providers = providers;
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> departments() {
        return departments.findAll(Sort.by("code").ascending().and(Sort.by("id"))).stream()
                .map(DepartmentMapper::toResponse).toList();
    }

    @GetMapping("/providers")
    public List<ServiceProviderResponse> providers() {
        return providers.findByActiveTrueOrderByNameAsc().stream()
                .map(ServiceProviderMapper::toResponse).toList();
    }
}
