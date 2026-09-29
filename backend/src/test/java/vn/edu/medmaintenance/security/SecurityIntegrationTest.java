package vn.edu.medmaintenance.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.medmaintenance.persistence.repository.UserAccountRepository;
import vn.edu.medmaintenance.security.jwt.JwtService;
import vn.edu.medmaintenance.security.auth.LoginRequest;
import vn.edu.medmaintenance.security.auth.LoginResponse;
import vn.edu.medmaintenance.security.auth.AuthenticatedUserResponse;
import vn.edu.medmaintenance.persistence.enums.UserRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired UserAccountRepository users;

    private ResponseEntity<JsonNode> login(String username, String password) {
        return http.postForEntity("/api/auth/login", Map.of("username", username, "password", password), JsonNode.class);
    }
    private String password(String role) {
        String value = System.getenv("DEMO_" + role + "_PASSWORD");
        assertThat(value).as("Source ignored backend-security.env").isNotBlank();
        return value;
    }
    private String token(String username, String role) {
        var response = login(username, password(role));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody().path("accessToken").asText();
    }
    private ResponseEntity<JsonNode> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
    private void assertError(ResponseEntity<JsonNode> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        JsonNode body = response.getBody();
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("path").asText()).isNotBlank();
        assertThat(body.path("timestamp").asText()).isNotBlank();
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.toString()).doesNotContain("Exception", "java.lang", "passwordHash", "JWT_SECRET", "stackTrace");
    }

    @Test
    void fourRolesLoginAndMeContainCurrentDepartmentContext() {
        String[][] roles = {{"demo_vtyt", "VTYT", "PHONG_VTYT"},
                {"demo_bgd", "BGD", "BAN_GIAM_DOC"},
                {"demo_khoa_noi", "KHOA", "KHOA_PHONG"},
                {"demo_admin", "ADMIN", "ADMIN"}};
        for (String[] row : roles) {
            var response = login(row[0], password(row[1]));
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            JsonNode body = response.getBody();
            assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
            assertThat(body.path("expiresIn").asLong()).isEqualTo(3600);
            assertThat(body.path("user").path("role").asText()).isEqualTo(row[2]);
            Long id = jdbc.queryForObject("SELECT id FROM user_account WHERE username=?", Long.class, row[0]);
            assertThat(body.at("/user/id").asLong()).isEqualTo(id);
            var me = get("/api/auth/me", body.path("accessToken").asText());
            assertThat(me.getStatusCode().value()).isEqualTo(200);
            assertThat(me.getBody().path("role").asText()).isEqualTo(row[2]);
            assertThat(me.getBody().path("id").asLong()).isEqualTo(id);
            Long department = jdbc.queryForObject("SELECT department_id FROM user_account WHERE id=?", Long.class, id);
            if (department == null) assertThat(me.getBody().path("departmentId").isNull()).isTrue();
            else assertThat(me.getBody().path("departmentId").asLong()).isEqualTo(department);
            assertThat(me.getBody().toString()).doesNotContain("password", "hash", "displayName");
        }
    }

    @Test
    void bearerClaimsAreMinimalAndExpireInOneHour() {
        String accessToken = token("demo_khoa_noi", "KHOA");
        var claims = jwt.verify(accessToken);
        Long id = jdbc.queryForObject("SELECT id FROM user_account WHERE username='demo_khoa_noi'", Long.class);
        Long department = jdbc.queryForObject("SELECT department_id FROM user_account WHERE id=?", Long.class, id);
        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("KHOA_PHONG");
        assertThat(((Number) claims.get("departmentId")).longValue()).isEqualTo(department);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond()).isEqualTo(3600);
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "role", "departmentId", "iat", "exp");
    }

    @Test
    void wrongUnknownAndInactiveCredentialsShareSafe401() {
        assertError(login("demo_vtyt", "incorrect"), 401, "INVALID_CREDENTIALS");
        assertError(login("nobody_demo", "incorrect"), 401, "INVALID_CREDENTIALS");
        jdbc.update("UPDATE user_account SET active=false WHERE username='demo_admin'");
        try {
            assertError(login("demo_admin", password("ADMIN")), 401, "INVALID_CREDENTIALS");
        } finally {
            jdbc.update("UPDATE user_account SET active=true WHERE username='demo_admin'");
        }
    }

    @Test
    void anonymousAndInvalidTokensGetJson401() {
        assertThat(get("/actuator/health", null).getBody().path("status").asText()).isEqualTo("UP");
        assertError(get("/api/auth/me", null), 401, "AUTHENTICATION_REQUIRED");
        assertError(get("/api/equipment", null), 401, "AUTHENTICATION_REQUIRED");
        assertError(get("/api/plans", "not-a-jwt"), 401, "INVALID_TOKEN");
        String valid = token("demo_vtyt", "VTYT");
        String altered = valid.substring(0, valid.lastIndexOf('.') + 1) + "AAAA";
        assertError(get("/api/auth/me", altered), 401, "INVALID_TOKEN");
        byte[] secret = Base64.getDecoder().decode(System.getenv("JWT_SECRET"));
        String expired = Jwts.builder().subject("1").claim("role", "PHONG_VTYT")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(secret)).compact();
        assertError(get("/api/auth/me", expired), 401, "INVALID_TOKEN");
    }

    @Test
    void roleBoundaryAndProtectedReadsWork() {
        String director = token("demo_bgd", "BGD");
        String vtyt = token("demo_vtyt", "VTYT");
        String department = token("demo_khoa_noi", "KHOA");
        String admin = token("demo_admin", "ADMIN");
        assertThat(get("/api/approvals/pending", director).getStatusCode().value()).isEqualTo(200);
        assertError(get("/api/approvals/pending", vtyt), 403, "ACCESS_DENIED");
        assertError(get("/api/approvals/pending", department), 403, "ACCESS_DENIED");
        assertError(get("/api/approvals/pending", admin), 403, "ACCESS_DENIED");
        assertThat(get("/api/equipment", department).getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/plans", admin).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void disabledUserAndRoleChangeTakeEffectBeforeTokenExpiry() {
        String accessToken = token("demo_admin", "ADMIN");
        jdbc.update("UPDATE user_account SET active=false WHERE username='demo_admin'");
        try {
            assertError(get("/api/auth/me", accessToken), 401, "INVALID_TOKEN");
        } finally {
            jdbc.update("UPDATE user_account SET active=true WHERE username='demo_admin'");
        }
        jdbc.update("UPDATE user_account SET role_code='BAN_GIAM_DOC' WHERE username='demo_admin'");
        try {
            assertThat(get("/api/approvals/pending", accessToken).getStatusCode().value()).isEqualTo(200);
            assertThat(get("/api/auth/me", accessToken).getBody().path("role").asText()).isEqualTo("BAN_GIAM_DOC");
        } finally {
            jdbc.update("UPDATE user_account SET role_code='ADMIN' WHERE username='demo_admin'");
        }
    }

    @Test
    void authDtoDebugRenderingNeverShowsPasswordOrToken() {
        String raw = password("VTYT");
        String accessToken = token("demo_vtyt", "VTYT");
        var user = new AuthenticatedUserResponse(1L, "demo_vtyt", UserRole.PHONG_VTYT, 1L);
        assertThat(new LoginRequest("demo_vtyt", raw).toString()).doesNotContain(raw);
        assertThat(new LoginResponse(accessToken, "Bearer", 3600, user).toString())
                .doesNotContain(accessToken);
    }

    @Test
    void bcryptVerifiesWithoutExposingStoredHash() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM user_account WHERE username='demo_vtyt'", String.class);
        assertThat(hash).startsWith("$2b$12$");
        assertThat(encoder.matches(password("VTYT"), hash)).isTrue();
        assertThat(encoder.matches("incorrect", hash)).isFalse();
        assertThat(hash).isNotEqualTo(password("VTYT"));
    }
}
