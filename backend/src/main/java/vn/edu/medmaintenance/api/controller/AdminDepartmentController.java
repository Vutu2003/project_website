package vn.edu.medmaintenance.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.dto.request.CatalogRequest;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.DepartmentResponse;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.service.AdminDepartmentService;

@RestController
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {
    private final AdminDepartmentService service;
    public AdminDepartmentController(AdminDepartmentService service) { this.service = service; }
    @GetMapping public PageResponse<DepartmentResponse> list(@ModelAttribute PageQuery page,
            @RequestParam(required = false) String search, @RequestParam(required = false) Boolean active) {
        return service.list(page, search, active);
    }
    @GetMapping("/{id}") public DepartmentResponse detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@RequestBody CatalogRequest request) { return service.create(request); }
    @PatchMapping("/{id}")
    public DepartmentResponse edit(@PathVariable Long id, @RequestBody CatalogRequest request) { return service.edit(id, request); }
    @PostMapping("/{id}/activate") public DepartmentResponse activate(@PathVariable Long id) { return service.activate(id); }
    @PostMapping("/{id}/deactivate") public DepartmentResponse deactivate(@PathVariable Long id) { return service.deactivate(id); }
}
