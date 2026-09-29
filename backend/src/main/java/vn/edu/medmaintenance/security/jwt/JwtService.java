package vn.edu.medmaintenance.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;

@Component
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${security.jwt.secret}") String encodedSecret,
            @Value("${security.jwt.expiration-seconds}") long expirationSeconds) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be Base64 encoded", exception);
        }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 random bytes");
        if (expirationSeconds < 60 || expirationSeconds > 3600) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be between 60 and 3600");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationSeconds = expirationSeconds;
    }

    public String issue(AuthenticatedUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder().subject(user.id().toString()).claim("role", user.role().name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(expirationSeconds)));
        if (user.departmentId() != null) builder.claim("departmentId", user.departmentId());
        return builder.signWith(key).compact();
    }

    public Claims verify(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long expirationSeconds() { return expirationSeconds; }
}
