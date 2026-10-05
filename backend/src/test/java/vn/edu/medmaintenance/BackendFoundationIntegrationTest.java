package vn.edu.medmaintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BackendFoundationIntegrationTest {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @Test
    void springContextAndDataSourceLoad() {
        assertThat(dataSource).isNotNull();
        assertThat(jdbc).isNotNull();
    }

    @Test
    void connectsToTheRealPostgresqlDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(connection.getCatalog()).isEqualTo(System.getenv().getOrDefault("DB_NAME", "medical_maintenance_backend_dev"));
        }
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void schemaMatchesTheFrozenBusinessInventory() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "department", "user_account", "equipment", "service_provider",
                "maintenance_coverage", "maintenance_plan", "maintenance_plan_item",
                "approval_request", "approval_action", "maintenance_execution",
                "maintenance_progress_log", "acceptance_record", "maintenance_report",
                "status_history", "user_notification", "maintenance_contract", "equipment_maintenance_schedule", "maintenance_contract_equipment", "maintenance_report_delivery");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """, Integer.class)).isEqualTo(158);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM pg_constraint c
                JOIN pg_namespace n ON n.oid = c.connamespace
                WHERE n.nspname = 'public' AND c.contype = 'f'
                """, Integer.class)).isEqualTo(42);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE success = true AND type = 'SQL'
                """, Integer.class)).isEqualTo(12);
    }

    @Test
    void actuatorHealthReportsUp() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
