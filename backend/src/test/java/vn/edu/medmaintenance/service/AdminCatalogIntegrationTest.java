package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCatalogIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    private String admin;
    private final String prefix = "SMOKE-V2-CATALOG-" + UUID.randomUUID();
    private Long departmentId, providerId, accountId;
    private String departmentCode, providerCode, username;

    @BeforeEach void login() {
        admin = token("demo_admin", System.getenv("DEMO_ADMIN_PASSWORD"));
    }
    @AfterEach void cleanup() {
        if (accountId != null)
            assertThat(jdbc.update("DELETE FROM user_account WHERE id=? AND username=?", accountId, username)).isEqualTo(1);
        if (departmentId != null)
            assertThat(jdbc.update("DELETE FROM department WHERE id=? AND code=?", departmentId, departmentCode)).isEqualTo(1);
        if (providerId != null)
            assertThat(jdbc.update("DELETE FROM service_provider WHERE id=? AND code=?", providerId, providerCode)).isEqualTo(1);
        accountId = departmentId = providerId = null;
    }
    private ResponseEntity<JsonNode> send(HttpMethod method, String path, String bearer, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (bearer != null) headers.setBearerAuth(bearer);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode status(ResponseEntity<JsonNode> response, int status) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        return response.getBody();
    }
    private String token(String username, String password) {
        return status(send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", username, "password", password)), 200).path("accessToken").asText();
    }
    @Test void departmentCrudSelectionAndHistoricalAccount() {
        departmentCode = prefix + "-DEPT";
        String base = "/api/admin/departments";
        var created = status(send(HttpMethod.POST, base, admin,
                Map.of("code", " " + departmentCode + " ", "name", "  Khoa thử nghiệm  ")), 201);
        departmentId = created.path("id").asLong();
        assertThat(created.path("code").asText()).isEqualTo(departmentCode);
        assertThat(created.path("name").asText()).isEqualTo("Khoa thử nghiệm");
        assertThat(created.path("active").asBoolean()).isTrue();
        assertThat(status(send(HttpMethod.GET, base + "?search=" + departmentCode + "&active=true", admin, null), 200)
                .path("totalElements").asInt()).isEqualTo(1);
        assertThat(status(send(HttpMethod.POST, base, admin,
                Map.of("code", departmentCode, "name", "Duplicate")), 409).path("code").asText())
                .isEqualTo("DEPARTMENT_CODE_ALREADY_EXISTS");
        status(send(HttpMethod.PATCH, base + "/" + departmentId, admin,
                Map.of("code", departmentCode, "name", "Khoa cập nhật")), 200);
        assertThat(status(send(HttpMethod.GET, base + "/" + departmentId, admin, null), 200)
                .path("name").asText()).isEqualTo("Khoa cập nhật");
        assertThat(status(send(HttpMethod.GET, "/api/departments", admin, null), 200).toString()).contains(departmentCode);

        username = prefix.toLowerCase() + "-user";
        String password = UUID.randomUUID().toString();
        var account = status(send(HttpMethod.POST, "/api/admin/accounts", admin,
                Map.of("username", username, "password", password, "role", "KHOA_PHONG",
                        "departmentId", departmentId, "active", true)), 201);
        accountId = account.path("id").asLong();
        String khoa = token(username, password);
        assertThat(status(send(HttpMethod.GET, "/api/auth/me", khoa, null), 200)
                .path("departmentId").asLong()).isEqualTo(departmentId);
        status(send(HttpMethod.POST, base + "/" + departmentId + "/deactivate", admin, null), 200);
        assertThat(status(send(HttpMethod.GET, base + "?search=" + departmentCode + "&active=false", admin, null), 200)
                .path("totalElements").asInt()).isEqualTo(1);
        assertThat(status(send(HttpMethod.GET, "/api/departments", admin, null), 200).toString()).doesNotContain(departmentCode);
        assertThat(status(send(HttpMethod.GET, "/api/admin/accounts/" + accountId, admin, null), 200)
                .path("departmentName").asText()).isEqualTo("Khoa cập nhật");
        assertThat(status(send(HttpMethod.PATCH, "/api/admin/accounts/" + accountId, admin,
                Map.of("role", "KHOA_PHONG", "departmentId", departmentId)), 200)
                .path("departmentId").asLong()).isEqualTo(departmentId);
        var invalid = status(send(HttpMethod.POST, "/api/admin/accounts", admin,
                Map.of("username", username + "2", "password", password, "role", "KHOA_PHONG",
                        "departmentId", departmentId, "active", true)), 400);
        assertThat(invalid.path("code").asText()).isEqualTo("INVALID_DEPARTMENT");
        status(send(HttpMethod.POST, base + "/" + departmentId + "/activate", admin, null), 200);
        assertThat(status(send(HttpMethod.GET, base + "/999999999", admin, null), 404)
                .path("code").asText()).isEqualTo("DEPARTMENT_NOT_FOUND");
    }
    @Test void providerCrudAndActiveSelection() {
        providerCode = prefix + "-PROVIDER";
        String base = "/api/admin/providers";
        var created = status(send(HttpMethod.POST, base, admin, Map.of(
                "code", " " + providerCode + " ", "name", " Đơn vị thử nghiệm ", "contactDetails", " liên hệ nội bộ ")), 201);
        providerId = created.path("id").asLong();
        assertThat(created.path("code").asText()).isEqualTo(providerCode);
        assertThat(created.path("contactDetails").asText()).isEqualTo("liên hệ nội bộ");
        assertThat(status(send(HttpMethod.GET, base + "?search=" + providerCode + "&active=true", admin, null), 200)
                .path("totalElements").asInt()).isEqualTo(1);
        assertThat(status(send(HttpMethod.POST, base, admin,
                Map.of("code", providerCode, "name", "Duplicate")), 409).path("code").asText())
                .isEqualTo("SERVICE_PROVIDER_CODE_ALREADY_EXISTS");
        status(send(HttpMethod.PATCH, base + "/" + providerId, admin,
                Map.of("code", providerCode, "name", "Đơn vị cập nhật", "contactDetails", " mới ")), 200);
        assertThat(status(send(HttpMethod.GET, "/api/providers", admin, null), 200).toString()).contains(providerCode);
        status(send(HttpMethod.POST, base + "/" + providerId + "/deactivate", admin, null), 200);
        assertThat(status(send(HttpMethod.GET, base + "?search=" + providerCode + "&active=false", admin, null), 200)
                .path("totalElements").asInt()).isEqualTo(1);
        assertThat(status(send(HttpMethod.GET, "/api/providers", admin, null), 200).toString()).doesNotContain(providerCode);
        assertThat(status(send(HttpMethod.GET, base + "/" + providerId, admin, null), 200)
                .path("name").asText()).isEqualTo("Đơn vị cập nhật");
        status(send(HttpMethod.POST, base + "/" + providerId + "/activate", admin, null), 200);
        assertThat(status(send(HttpMethod.GET, base + "/999999999", admin, null), 404)
                .path("code").asText()).isEqualTo("SERVICE_PROVIDER_NOT_FOUND");
    }
    @Test void nonAdminCannotReadOrWriteCatalogs() {
        String[] roles = {"DEMO_VTYT_PASSWORD", "DEMO_BGD_PASSWORD", "DEMO_KHOA_PASSWORD"};
        String[] users = {"demo_vtyt", "demo_bgd", "demo_khoa_noi"};
        for (int i = 0; i < roles.length; i++) {
            String bearer = token(users[i], System.getenv(roles[i]));
            for (String path : new String[] {"/api/admin/departments", "/api/admin/providers"}) {
                status(send(HttpMethod.GET, path, bearer, null), 403);
                status(send(HttpMethod.POST, path, bearer, Map.of("code", prefix, "name", "Denied")), 403);
            }
        }
    }
}
