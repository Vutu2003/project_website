package vn.edu.medmaintenance.api.dto.response;

import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.UserRole;

public record AccountResponse(Long id, String username, UserRole role, Long departmentId,
        String departmentCode, String departmentName, Boolean active) {
    public static AccountResponse from(UserAccount account) {
        var department = account.getDepartment();
        return new AccountResponse(account.getId(), account.getUsername(), account.getRoleCode(),
                department == null ? null : department.getId(),
                department == null ? null : department.getCode(),
                department == null ? null : department.getName(), account.getActive());
    }
}
