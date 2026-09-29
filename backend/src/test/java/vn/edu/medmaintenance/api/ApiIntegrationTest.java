package vn.edu.medmaintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    private String token;

    @BeforeEach
    void authenticate() {
        String password = System.getenv("DEMO_BGD_PASSWORD");
        assertThat(password).as("Source .local-postgres/backend-security.env").isNotBlank();
        var response = http.postForEntity("/api/auth/login",
                new java.util.HashMap<>(java.util.Map.of("username", "demo_bgd", "password", password)), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        token = response.getBody().path("accessToken").asText();
    }

    private ResponseEntity<JsonNode> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    @Test
    void healthAndReferenceEndpointsReadRealSeedData() {
        assertThat(get("/actuator/health").getBody().path("status").asText()).isEqualTo("UP");
        var departments = get("/api/departments");
        assertThat(departments.getStatusCode().value()).isEqualTo(200);
        assertThat(departments.getBody()).hasSize(8);
        assertThat(departments.getBody().toString()).contains("HOI_SUC");
        var providers = get("/api/providers");
        assertThat(providers.getStatusCode().value()).isEqualTo(200);
        assertThat(providers.getBody()).hasSize(7);
        providers.getBody().forEach(row -> assertThat(row.path("active").asBoolean()).isTrue());
    }

    @Test
    void equipmentListPaginatesFiltersAndSortsUsingAllowlistedFields() {
        var first = get("/api/equipment?page=0&size=10&sort=equipmentCode,asc");
        var second = get("/api/equipment?page=1&size=10&sort=equipmentCode,asc");
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getBody().path("content")).hasSize(10);
        assertThat(first.getBody().path("totalElements").asInt()).isEqualTo(40);
        assertThat(first.getBody().path("totalPages").asInt()).isEqualTo(4);
        assertThat(first.getBody().path("last").asBoolean()).isFalse();
        assertThat(first.getBody().at("/content/0/equipmentCode").asText())
                .isLessThan(first.getBody().at("/content/1/equipmentCode").asText());
        assertThat(first.getBody().at("/content/0/id").asLong())
                .isNotEqualTo(second.getBody().at("/content/0/id").asLong());
        assertThat(first.getBody().at("/content/0/departmentName").asText()).isNotBlank();

        Long departmentId = jdbc.queryForObject("SELECT id FROM department WHERE code = 'HOI_SUC'", Long.class);
        var filtered = get("/api/equipment?departmentId=" + departmentId + "&active=true&size=20");
        assertThat(filtered.getStatusCode().value()).isEqualTo(200);
        assertThat(filtered.getBody().path("totalElements").asInt()).isEqualTo(8);
        filtered.getBody().path("content").forEach(e ->
                assertThat(e.path("departmentCode").asText()).isEqualTo("HOI_SUC"));
        assertThat(get("/api/equipment?active=false").getBody().path("totalElements").asInt()).isZero();

        var descending = get("/api/equipment?size=2&sort=equipmentCode,desc");
        assertThat(descending.getBody().at("/content/0/equipmentCode").asText())
                .isGreaterThan(descending.getBody().at("/content/1/equipmentCode").asText());
    }

    @Test
    void equipmentDetailUsesDtoAndMissingIdReturns404() {
        Long id = jdbc.queryForObject("SELECT id FROM equipment WHERE equipment_code = 'DEMO-EQ-004'", Long.class);
        var found = get("/api/equipment/" + id);
        assertThat(found.getStatusCode().value()).isEqualTo(200);
        assertThat(found.getBody().path("equipmentCode").asText()).isEqualTo("DEMO-EQ-004");
        assertThat(found.getBody().path("departmentName").asText()).isNotBlank();
        assertThat(found.getBody().has("technicalSpec")).isFalse();
        assertError(get("/api/equipment/999999999"), 404, "RESOURCE_NOT_FOUND", "/api/equipment/999999999");
        assertError(get("/api/equipment/0"), 400, "INVALID_PARAMETER", "/api/equipment/0");
    }

    @Test
    void invalidPagingSortingAndFilterInputsReturnStructured400() {
        var invalidPage = get("/api/equipment?page=-1");
        assertError(invalidPage, 400, "VALIDATION_ERROR", "/api/equipment");
        assertThat(invalidPage.getBody().path("fieldErrors")).hasSize(1);
        assertThat(invalidPage.getBody().at("/fieldErrors/0/field").asText()).isEqualTo("page");
        assertError(get("/api/equipment?size=0"), 400, "VALIDATION_ERROR", "/api/equipment");
        assertError(get("/api/equipment?size=101"), 400, "VALIDATION_ERROR", "/api/equipment");
        assertError(get("/api/equipment?sort=department.passwordHash"), 400,
                "INVALID_PARAMETER", "/api/equipment");
        assertError(get("/api/equipment?sort=name,sideways"), 400,
                "INVALID_PARAMETER", "/api/equipment");
        assertError(get("/api/equipment?departmentId=0"), 400,
                "INVALID_PARAMETER", "/api/equipment");
        assertError(get("/api/equipment?active=maybe"), 400,
                "INVALID_PARAMETER", "/api/equipment");
    }

    @Test
    void plansSupportPagedStatusFilterAndIsoDateSerialization() {
        var all = get("/api/plans?page=0&size=3&sort=periodStart,desc");
        assertThat(all.getStatusCode().value()).isEqualTo(200);
        assertThat(all.getBody().path("content")).hasSize(3);
        assertThat(all.getBody().path("totalElements").asInt()).isEqualTo(8);
        assertThat(LocalDate.parse(all.getBody().at("/content/0/periodStart").asText())).isNotNull();
        assertThat(OffsetDateTime.parse(all.getBody().at("/content/0/createdAt").asText())).isNotNull();
        assertThat(all.getBody().at("/content/0/status").asText()).isNotBlank();
        var approved = get("/api/plans?status=APPROVED");
        assertThat(approved.getBody().path("totalElements").asInt()).isEqualTo(1);
        assertThat(approved.getBody().at("/content/0/status").asText()).isEqualTo("APPROVED");
        assertError(get("/api/plans?status=INVALID"), 400, "INVALID_PARAMETER", "/api/plans");
        assertError(get("/api/plans?sort=createdByUser.passwordHash"), 400,
                "INVALID_PARAMETER", "/api/plans");
    }

    @Test
    void planDetailAndItemsReturnMappedDtosAnd404ForMissingPlan() {
        Long planId = jdbc.queryForObject("SELECT id FROM maintenance_plan WHERE status = 'CLOSED'", Long.class);
        var detail = get("/api/plans/" + planId);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(detail.getBody().path("status").asText()).isEqualTo("CLOSED");
        assertThat(detail.getBody().path("createdByName").asText()).isNotBlank();
        assertThat(detail.getBody().has("createdByUser")).isFalse();
        var items = get("/api/plans/" + planId + "/items?size=3");
        assertThat(items.getStatusCode().value()).isEqualTo(200);
        assertThat(items.getBody().path("content")).hasSize(3);
        assertThat(items.getBody().path("totalElements").asInt()).isGreaterThan(3);
        assertThat(items.getBody().at("/content/0/equipmentCode").asText()).isNotBlank();
        assertThat(items.getBody().at("/content/0/departmentNameAtPlan").asText()).isNotBlank();
        assertError(get("/api/plans/999999999"), 404, "RESOURCE_NOT_FOUND", "/api/plans/999999999");
        assertError(get("/api/plans/0"), 400, "INVALID_PARAMETER", "/api/plans/0");
        assertError(get("/api/plans/" + planId + "/items?size=0"), 400,
                "VALIDATION_ERROR", "/api/plans/" + planId + "/items");
        assertError(get("/api/plans/999999999/items"), 404,
                "RESOURCE_NOT_FOUND", "/api/plans/999999999/items");
    }

    @Test
    void pendingApprovalQueueFiltersBothFrozenTypes() {
        var all = get("/api/approvals/pending?size=2");
        assertThat(all.getStatusCode().value()).isEqualTo(200);
        assertThat(all.getBody().path("totalElements").asInt()).isEqualTo(3);
        assertThat(all.getBody().path("content")).hasSize(2);
        var plans = get("/api/approvals/pending?requestType=PLAN_APPROVAL");
        assertThat(plans.getBody().path("totalElements").asInt()).isEqualTo(1);
        assertThat(plans.getBody().at("/content/0/planTitle").asText()).isNotBlank();
        var vendors = get("/api/approvals/pending?requestType=VENDOR_SELECTION");
        assertThat(vendors.getBody().path("totalElements").asInt()).isEqualTo(2);
        assertThat(vendors.getBody().at("/content/0/equipmentCode").asText()).isNotBlank();
        assertThat(vendors.getBody().at("/content/0/proposedProviderName").asText()).isNotBlank();
        assertError(get("/api/approvals/pending?requestType=UNKNOWN"), 400,
                "INVALID_PARAMETER", "/api/approvals/pending");
        assertError(get("/api/approvals/pending?size=101"), 400,
                "VALIDATION_ERROR", "/api/approvals/pending");
    }

    @Test
    void unsupportedWriteMethodUses405AndSafeErrorBody() {
        assertError(post("/api/equipment"),
                405, "METHOD_NOT_ALLOWED", "/api/equipment");
        assertError(get("/api/nonexistent"), 404, "RESOURCE_NOT_FOUND", "/api/nonexistent");
    }

    @Test
    void successJsonNeverSerializesEntityGraphsOrCredentialFields() {
        Long planId = jdbc.queryForObject("SELECT id FROM maintenance_plan WHERE status = 'CLOSED'", Long.class);
        for (String path : new String[] {"/api/departments", "/api/equipment?size=20",
                "/api/plans?size=8", "/api/plans/" + planId + "/items?size=20",
                "/api/approvals/pending", "/api/providers"}) {
            var response = get(path);
            assertThat(response.getStatusCode().value()).as(path).isEqualTo(200);
            String json = response.getBody().toString();
            assertThat(json).doesNotContain("hibernateLazyInitializer", "passwordHash", "passwordDigest",
                    "technicalSpec", "\"handler\"", "\"createdByUser\":{",
                    "\"department\":{", "\"plan\":{");
        }
    }

    private ResponseEntity<JsonNode> post(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(headers), JsonNode.class);
    }

    private static void assertError(ResponseEntity<JsonNode> response, int status, String code, String path) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        JsonNode body = response.getBody();
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("error").asText()).isNotBlank();
        assertThat(body.path("message").asText()).isNotBlank();
        assertThat(body.path("path").asText()).isEqualTo(path);
        assertThat(OffsetDateTime.parse(body.path("timestamp").asText())).isNotNull();
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.toString()).doesNotContain("stackTrace", "java.lang.", "org.postgresql", "SQLState");
    }
}
