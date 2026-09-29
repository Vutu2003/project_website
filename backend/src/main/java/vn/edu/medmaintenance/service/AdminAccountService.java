package vn.edu.medmaintenance.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.*;
import vn.edu.medmaintenance.api.dto.response.AccountResponse;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.persistence.entity.Department;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.DepartmentRepository;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
@Transactional(readOnly = true)
public class AdminAccountService {
    private final UserAccountRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder passwords;
    private final CurrentUser currentUser;

    public AdminAccountService(UserAccountRepository users, DepartmentRepository departments,
            PasswordEncoder passwords, CurrentUser currentUser) {
        this.users = users; this.departments = departments;
        this.passwords = passwords; this.currentUser = currentUser;
    }

    public PageResponse<AccountResponse> list(PageQuery page, String search, UserRole role,
            Long departmentId, Boolean active) {
        requireAdmin();
        if (departmentId != null) PageRequests.requirePositive(departmentId, "departmentId");
        String term = search == null ? "" : search.trim();
        if (term.length() > 100) bad("INVALID_USERNAME", "Từ khóa tối đa 100 ký tự.");
        String pattern = "%" + term.toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<UserAccount> filter = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!term.isEmpty()) predicates.add(cb.like(cb.lower(root.get("username")), pattern, '\\'));
            if (role != null) predicates.add(cb.equal(root.get("roleCode"), role));
            if (departmentId != null) predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageResponse.from(users.findAll(filter, PageRequests.create(page,
                Set.of("id", "username", "active"), "username", Sort.Direction.ASC)), AccountResponse::from);
    }

    public AccountResponse detail(Long id) {
        requireAdmin(); PageRequests.requirePositive(id, "id");
        return AccountResponse.from(users.findWithDepartmentById(id).orElseThrow(this::notFound));
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest command) {
        requireAdmin();
        String username = command.username() == null ? "" : command.username().trim();
        if (username.isBlank() || username.length() > 100)
            bad("INVALID_USERNAME", "Tên đăng nhập bắt buộc, tối đa 100 ký tự.");
        validatePassword(command.password());
        Department department = department(command.role(), command.departmentId());
        if (command.active() == null) bad("INVALID_ACCOUNT_STATUS", "Trạng thái tài khoản bắt buộc.");
        if (users.findByUsername(username).isPresent()) duplicate();
        var account = UserAccount.createLoginAccount(username, passwords.encode(command.password()),
                command.role(), department, command.active());
        try { users.saveAndFlush(account); }
        catch (DataIntegrityViolationException failure) {
            // The database constraint is also authoritative for concurrent create requests.
            if (String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uq_user_account_username")) duplicate();
            throw failure;
        }
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse edit(Long id, UpdateAccountRequest command) {
        requireAdmin();
        Department department = department(command.role(), command.departmentId());
        var account = locked(id);
        account.setRoleCode(command.role()); account.setDepartment(department);
        users.flush();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse activate(Long id) { return setActive(id, true); }

    @Transactional
    public AccountResponse deactivate(Long id) { return setActive(id, false); }

    private AccountResponse setActive(Long id, boolean active) {
        requireAdmin();
        if (!active && currentUser.get().id().equals(id))
            throw new BusinessRuleException(HttpStatus.CONFLICT, "ACCOUNT_SELF_DEACTIVATION_FORBIDDEN",
                    "Bạn không thể vô hiệu hóa tài khoản ADMIN đang đăng nhập của chính mình.");
        var account = locked(id);
        account.setActive(active); users.flush();
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse resetPassword(Long id, ResetAccountPasswordRequest command) {
        requireAdmin(); validatePassword(command.newPassword());
        var account = locked(id);
        account.setPasswordHash(passwords.encode(command.newPassword())); users.flush();
        return AccountResponse.from(account);
    }

    private UserAccount locked(Long id) {
        PageRequests.requirePositive(id, "id");
        return users.findForUpdateById(id).orElseThrow(this::notFound);
    }

    private Department department(UserRole role, Long id) {
        if (role == null) bad("INVALID_ACCOUNT_ROLE", "Vai trò tài khoản bắt buộc.");
        if (id == null) {
            if (role == UserRole.KHOA_PHONG) bad("DEPARTMENT_REQUIRED", "Khoa/Phòng bắt buộc với vai trò Khoa/Phòng.");
            return null;
        }
        if (id <= 0) bad("INVALID_DEPARTMENT", "Khoa/Phòng không hợp lệ.");
        var department = departments.findById(id).orElseThrow(() ->
                new BusinessRuleException(HttpStatus.BAD_REQUEST, "INVALID_DEPARTMENT", "Khoa/Phòng không tồn tại."));
        if (!Boolean.TRUE.equals(department.getActive())) bad("INVALID_DEPARTMENT", "Khoa/Phòng không còn hoạt động.");
        return department;
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank()) bad("INVALID_PASSWORD", "Mật khẩu không được để trống.");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            bad("INVALID_PASSWORD", "Mật khẩu tối đa 72 byte UTF-8 để phù hợp BCrypt.");
    }
    private void requireAdmin() {
        if (currentUser.get().role() != UserRole.ADMIN)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Chỉ ADMIN được quản lý tài khoản.");
    }
    private BusinessRuleException notFound() {
        return new BusinessRuleException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản.");
    }
    private void duplicate() {
        throw new BusinessRuleException(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "Tên đăng nhập đã tồn tại.");
    }
    private void bad(String code, String message) { throw new BusinessRuleException(HttpStatus.BAD_REQUEST, code, message); }
}
