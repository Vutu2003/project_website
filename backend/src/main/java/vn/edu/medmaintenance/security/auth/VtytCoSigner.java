package vn.edu.medmaintenance.security.auth;

import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Component;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.jwt.JwtService;

@Component
public class VtytCoSigner {
    private final JwtService jwt;
    private final UserAccountRepository users;

    public VtytCoSigner(JwtService jwt, UserAccountRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    public UserAccount authenticate(String bearerHeader) {
        if (bearerHeader == null || !bearerHeader.startsWith("Bearer ") || bearerHeader.length() <= 7)
            return null;
        try {
            Long id = Long.parseLong(jwt.verify(bearerHeader.substring(7)).getSubject());
            UserAccount user = users.findById(id).orElse(null);
            return user != null && Boolean.TRUE.equals(user.getActive())
                    && user.getRoleCode() == UserRole.PHONG_VTYT ? user : null;
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }
}
