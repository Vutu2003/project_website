package vn.edu.medmaintenance.security.auth;

import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;

/** Public identity shape; independent of the SecurityContext principal. */
public record AuthenticatedUserResponse(Long id, String username, UserRole role, Long departmentId) {
    public static AuthenticatedUserResponse from(AuthenticatedUser user) {
        return new AuthenticatedUserResponse(user.id(), user.username(), user.role(), user.departmentId());
    }
}
