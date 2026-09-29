package vn.edu.medmaintenance.security.principal;

import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.UserRole;

public record AuthenticatedUser(Long id, String username, UserRole role, Long departmentId) {
    public static AuthenticatedUser from(UserAccount account) {
        return new AuthenticatedUser(account.getId(), account.getUsername(), account.getRoleCode(),
                account.getDepartment() == null ? null : account.getDepartment().getId());
    }
}
