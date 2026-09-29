package vn.edu.medmaintenance.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowReadIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    private String token(String account, String passwordKey) {
        var response = http.postForEntity("/api/auth/login",
                Map.of("username", account, "password", System.getenv(passwordKey)), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody().path("accessToken").asText();
    }

    private ResponseEntity<JsonNode> get(String path, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    @Test
    void coverageEvidenceIsSelectableOnlyByVtyt() {
        Long equipmentId = jdbc.queryForObject(
                "SELECT equipment_id FROM maintenance_coverage WHERE classification='FREE' ORDER BY id LIMIT 1",
                Long.class);
        String path = "/api/equipment/" + equipmentId + "/coverages";
        var vtyt = get(path, token("demo_vtyt", "DEMO_VTYT_PASSWORD"));
        assertThat(vtyt.getStatusCode().value()).isEqualTo(200);
        assertThat(vtyt.getBody().isArray()).isTrue();
        assertThat(vtyt.getBody().size()).isGreaterThan(0);
        assertThat(vtyt.getBody().toString()).contains("classification", "verifiedAt", "basisNote");
        assertThat(vtyt.getBody().toString()).doesNotContain("passwordHash", "technicalSpec");
        assertThat(get(path, token("demo_bgd", "DEMO_BGD_PASSWORD")).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void pendingReviewExposesActualVendorRationaleAndVersionsOnlyToDirector() {
        Long requestId = jdbc.queryForObject("""
                SELECT id FROM approval_request WHERE request_type='VENDOR_SELECTION' AND status='PENDING'
                ORDER BY id LIMIT 1
                """, Long.class);
        String path = "/api/approvals/" + requestId;
        var director = get(path, token("demo_bgd", "DEMO_BGD_PASSWORD"));
        assertThat(director.getStatusCode().value()).isEqualTo(200);
        assertThat(director.getBody().path("requestType").asText()).isEqualTo("VENDOR_SELECTION");
        assertThat(director.getBody().path("itemVersion").isInt()).isTrue();
        assertThat(director.getBody().path("rationale").asText()).isNotBlank();
        assertThat(director.getBody().path("planId").asLong()).isPositive();
        assertThat(get(path, token("demo_vtyt", "DEMO_VTYT_PASSWORD")).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void savedVendorDraftCanBeResumedWithoutMutation() {
        Long itemId = jdbc.queryForObject("""
                SELECT plan_item_id FROM approval_request WHERE request_type='VENDOR_SELECTION' AND status='DRAFT'
                ORDER BY id LIMIT 1
                """, Long.class);
        String path = "/api/plan-items/" + itemId + "/vendor-proposals/draft";
        var draft = get(path, token("demo_vtyt", "DEMO_VTYT_PASSWORD"));
        assertThat(draft.getStatusCode().value()).isEqualTo(200);
        assertThat(draft.getBody().path("itemId").asLong()).isEqualTo(itemId);
        assertThat(draft.getBody().path("id").asLong()).isPositive();
        assertThat(draft.getBody().path("itemVersion").isInt()).isTrue();
        assertThat(get(path, token("demo_admin", "DEMO_ADMIN_PASSWORD")).getStatusCode().value()).isEqualTo(403);
    }
}
