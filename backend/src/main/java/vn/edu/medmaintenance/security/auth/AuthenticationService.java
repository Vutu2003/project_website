package vn.edu.medmaintenance.security.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.jwt.JwtService;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;

@Service
public class AuthenticationService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;

    public AuthenticationService(UserAccountRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    public LoginResponse login(LoginRequest request) {
        var account = users.findByUsername(request.username()).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getActive())
                || !passwords.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        AuthenticatedUser principal = AuthenticatedUser.from(account);
        return new LoginResponse(jwt.issue(principal), "Bearer", jwt.expirationSeconds(), AuthenticatedUserResponse.from(principal));
    }
}
