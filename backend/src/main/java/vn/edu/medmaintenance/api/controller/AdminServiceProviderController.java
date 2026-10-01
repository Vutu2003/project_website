package vn.edu.medmaintenance.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.dto.request.CatalogRequest;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.api.dto.response.ServiceProviderResponse;
import vn.edu.medmaintenance.service.AdminServiceProviderService;

@RestController
@RequestMapping("/api/admin/providers")
public class AdminServiceProviderController {
    private final AdminServiceProviderService service;
    public AdminServiceProviderController(AdminServiceProviderService service) { this.service = service; }
    @GetMapping public PageResponse<ServiceProviderResponse> list(@ModelAttribute PageQuery page,
            @RequestParam(required = false) String search, @RequestParam(required = false) Boolean active) {
        return service.list(page, search, active);
    }
    @GetMapping("/{id}") public ServiceProviderResponse detail(@PathVariable Long id) { return service.detail(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ServiceProviderResponse create(@RequestBody CatalogRequest request) { return service.create(request); }
    @PatchMapping("/{id}")
    public ServiceProviderResponse edit(@PathVariable Long id, @RequestBody CatalogRequest request) { return service.edit(id, request); }
    @PostMapping("/{id}/activate") public ServiceProviderResponse activate(@PathVariable Long id) { return service.activate(id); }
    @PostMapping("/{id}/deactivate") public ServiceProviderResponse deactivate(@PathVariable Long id) { return service.deactivate(id); }
}
