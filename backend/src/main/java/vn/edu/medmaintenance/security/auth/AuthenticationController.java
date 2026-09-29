package vn.edu.medmaintenance.security.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authentication;
    private final CurrentUser currentUser;

    public AuthenticationController(AuthenticationService authentication, CurrentUser currentUser) {
        this.authentication = authentication;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(authentication.login(request));
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me() { return AuthenticatedUserResponse.from(currentUser.get()); }
}
