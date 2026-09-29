package vn.edu.medmaintenance.security.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.exception.SecurityErrorWriter;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserAccountRepository users;
    private final SecurityErrorWriter errors;

    public JwtAuthenticationFilter(JwtService jwt, UserAccountRepository users, SecurityErrorWriter errors) {
        this.jwt = jwt;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals("/api/auth/login")
                || request.getRequestURI().equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ") || header.length() <= 7 || header.substring(7).isBlank()) {
            errors.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid bearer token");
            return;
        }
        try {
            String subject = jwt.verify(header.substring(7)).getSubject();
            Long id = Long.parseLong(subject);
            UserAccount account = users.findWithDepartmentById(id).orElse(null);
            if (account == null || !Boolean.TRUE.equals(account.getActive())) {
                errors.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid bearer token");
                return;
            }
            AuthenticatedUser principal = AuthenticatedUser.from(account);
            var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().name());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of(authority));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            errors.write(request, response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid bearer token");
            return;
        }
        chain.doFilter(request, response);
    }
}
