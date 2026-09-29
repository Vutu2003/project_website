package vn.edu.medmaintenance.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.medmaintenance.api.dto.request.*;
import vn.edu.medmaintenance.api.dto.response.AccountResponse;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.service.AdminAccountService;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {
    private final AdminAccountService accounts;
    public AdminAccountController(AdminAccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public PageResponse<AccountResponse> list(@Valid @ModelAttribute PageQuery page,
            @RequestParam(required = false) String search, @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Long departmentId, @RequestParam(required = false) Boolean active) {
        return accounts.list(page, search, role, departmentId, active);
    }
    @GetMapping("/{id}") public AccountResponse detail(@PathVariable Long id) { return accounts.detail(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) { return accounts.create(request); }
    @PatchMapping("/{id}")
    public AccountResponse edit(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) { return accounts.edit(id, request); }
    @PostMapping("/{id}/activate") public AccountResponse activate(@PathVariable Long id) { return accounts.activate(id); }
    @PostMapping("/{id}/deactivate") public AccountResponse deactivate(@PathVariable Long id) { return accounts.deactivate(id); }
    @PostMapping("/{id}/reset-password")
    public AccountResponse reset(@PathVariable Long id, @Valid @RequestBody ResetAccountPasswordRequest request) { return accounts.resetPassword(id, request); }
}
