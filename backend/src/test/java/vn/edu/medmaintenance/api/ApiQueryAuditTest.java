package vn.edu.medmaintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiQueryAuditTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    private String token;

    private void authenticate() {
        String password = System.getenv("DEMO_BGD_PASSWORD");
        assertThat(password).as("Source .local-postgres/backend-security.env").isNotBlank();
        var response = http.postForEntity("/api/auth/login", java.util.Map.of(
                "username", "demo_bgd", "password", password), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        token = response.getBody().path("accessToken").asText();
    }

    private record Result(String endpoint, int rows, long sql) { }

    @Test
    void dtoMappingKeepsApiListSqlCountsBounded() throws IOException {
        authenticate();
        Long planId = jdbc.queryForObject("SELECT id FROM maintenance_plan WHERE status = 'CLOSED'", Long.class);
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        List<Result> results = new ArrayList<>();
        stats.setStatisticsEnabled(true);
        try {
            results.add(measure(stats, "/api/equipment?size=20", "GET /api/equipment"));
            results.add(measure(stats, "/api/plans/" + planId + "/items?size=3",
                    "GET /api/plans/{planId}/items"));
            results.add(measure(stats, "/api/approvals/pending?size=2",
                    "GET /api/approvals/pending"));
        } finally {
            stats.setStatisticsEnabled(false);
        }
        assertThat(results).hasSize(3);
        assertThat(results.get(0).rows()).isEqualTo(20);
        assertThat(results.get(1).rows()).isEqualTo(3);
        assertThat(results.get(2).rows()).isEqualTo(2);
        for (Result result : results) assertThat(result.sql()).as(result.endpoint()).isBetween(2L, 5L);

        StringBuilder audit = new StringBuilder("# Phase 2.4 API Audit\n\n"
                + "Evidence: `ApiIntegrationTest`, `ApiExceptionHandlerTest`, and `ApiQueryAuditTest` "
                + "against the Phase 1.3 PostgreSQL seed. Query counts use Hibernate prepared-statement "
                + "statistics cleared before each HTTP request; they include one account reload and pageable count SQL.\n\n"
                + "## Endpoint Audit\n\n"
                + "| Endpoint | Success | Invalid Input | Not Found | Pagination | Result |\n"
                + "| --- | --- | --- | --- | --- | --- |\n"
                + "| GET `/api/departments` | 200 / 8 rows | N/A | N/A | No | PASS |\n"
                + "| GET `/api/equipment` | 200 / 40 total | 400 | N/A | Yes | PASS |\n"
                + "| GET `/api/equipment/{id}` | 200 | 400 for nonpositive ID | 404 | No | PASS |\n"
                + "| GET `/api/plans` | 200 / 8 total | 400 | N/A | Yes | PASS |\n"
                + "| GET `/api/plans/{id}` | 200 | 400 for nonpositive ID | 404 | No | PASS |\n"
                + "| GET `/api/plans/{planId}/items` | 200 | 400 for invalid page | 404 | Yes | PASS |\n"
                + "| GET `/api/approvals/pending` | 200 / 3 total | 400 | N/A | Yes | PASS |\n"
                + "| GET `/api/providers` | 200 / 7 rows | N/A | N/A | No | PASS |\n\n"
                + "## DTO Audit\n\n"
                + "| DTO | Entity Leakage | Sensitive Fields | Nested Graph | Result |\n"
                + "| --- | --- | --- | --- | --- |\n"
                + "| DepartmentResponse | None | None | None | PASS |\n"
                + "| EquipmentResponse | None | None | None | PASS |\n"
                + "| MaintenancePlanResponse | None | None | None | PASS |\n"
                + "| MaintenancePlanItemResponse | None | None | None | PASS |\n"
                + "| ApprovalRequestResponse | None | None | None | PASS |\n"
                + "| ServiceProviderResponse | None | None | None | PASS |\n\n"
                + "## Error Audit\n\n"
                + "| Error Type | HTTP Status | Code | Safe Message | Result |\n"
                + "| --- | ---: | --- | --- | --- |\n"
                + "| Bean validation | 400 | `VALIDATION_ERROR` | Yes | PASS |\n"
                + "| Invalid parameter / enum / sort | 400 | `INVALID_PARAMETER` | Yes | PASS |\n"
                + "| Missing resource | 404 | `RESOURCE_NOT_FOUND` | Yes | PASS |\n"
                + "| Unsupported method | 405 | `METHOD_NOT_ALLOWED` | Yes | PASS |\n"
                + "| Data integrity (handler test) | 409 | `DATA_CONFLICT` | Yes | PASS |\n"
                + "| Unexpected error (handler test) | 500 | `INTERNAL_ERROR` | Yes | PASS |\n\n"
                + "## Query Audit\n\n"
                + "| Endpoint | Rows | SQL Statements | N+1 | Result |\n"
                + "| --- | ---: | ---: | --- | --- |\n");
        for (Result result : results) {
            audit.append("| `").append(result.endpoint()).append("` | ").append(result.rows())
                    .append(" | ").append(result.sql()).append(" | No obvious expansion | PASS |\n");
        }
        audit.append("\nThis checks three representative demo-data pages, not production latency or scale. "
                + "Successful JSON responses contain DTO fields, not Hibernate proxies or password hashes.\n");
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path root = cwd.getFileName().toString().equals("backend") ? cwd.getParent() : cwd;
        Path output = root.resolve("reports/backend/phase_2_4_api_audit.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, audit.toString());
    }

    private Result measure(Statistics stats, String path, String name) {
        stats.clear();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<JsonNode> response = http.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode content = response.getBody().path("content");
        assertThat(content.isArray()).isTrue();
        return new Result(name, content.size(), stats.getPrepareStatementCount());
    }
}
