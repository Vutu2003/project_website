package vn.edu.medmaintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.env.Environment;

@SpringBootTest
class PersistenceMappingAuditTest {
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "department", "user_account", "equipment", "service_provider",
            "maintenance_coverage", "maintenance_plan", "maintenance_plan_item",
            "approval_request", "approval_action", "maintenance_execution",
            "maintenance_progress_log", "acceptance_record", "maintenance_report", "status_history");
    private static final Map<String, String> ENUM_CHECKS = Map.ofEntries(
            Map.entry("user_account.role_code", "ck_user_account_role"),
            Map.entry("maintenance_coverage.classification", "ck_coverage_classification"),
            Map.entry("maintenance_plan.status", "ck_plan_status"),
            Map.entry("maintenance_plan_item.status", "ck_item_status"),
            Map.entry("maintenance_plan_item.assignment_route", "ck_item_route"),
            Map.entry("approval_request.request_type", "ck_request_type"),
            Map.entry("approval_request.status", "ck_request_status"),
            Map.entry("approval_action.outcome", "ck_action_outcome"),
            Map.entry("acceptance_record.acceptance_type", "ck_acceptance_type"),
            Map.entry("acceptance_record.result", "ck_acceptance_result"),
            Map.entry("maintenance_report.status", "ck_report_status"));

    private static final Map<String, Set<String>> FROZEN_ENUMS = Map.ofEntries(
            Map.entry("user_account.role_code", Set.of("PHONG_VTYT", "BAN_GIAM_DOC", "KHOA_PHONG", "ADMIN")),
            Map.entry("maintenance_coverage.classification", Set.of("UNKNOWN", "FREE", "NOT_FREE")),
            Map.entry("maintenance_plan.status", Set.of("DRAFT", "SUBMITTED", "REVISION_REQUIRED", "APPROVED", "IN_PROGRESS", "AWAITING_REPORT", "REPORTED", "CLOSED")),
            Map.entry("maintenance_plan_item.status", Set.of("PLANNED", "UNDER_CONTRACT", "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL", "ASSIGNED_EXTERNAL", "IN_MAINTENANCE", "AWAITING_TECHNICAL_ACCEPTANCE", "AWAITING_HANDOVER", "COMPLETED", "REWORK_REQUIRED", "REPAIR_REQUIRED")),
            Map.entry("maintenance_plan_item.assignment_route", Set.of("UNDER_CONTRACT", "EXTERNAL_APPROVED")),
            Map.entry("approval_request.request_type", Set.of("PLAN_APPROVAL", "VENDOR_SELECTION")),
            Map.entry("approval_request.status", Set.of("DRAFT", "PENDING", "DECIDED")),
            Map.entry("approval_action.outcome", Set.of("APPROVE", "REVISION_REQUIRED")),
            Map.entry("acceptance_record.acceptance_type", Set.of("TECHNICAL_ACCEPTANCE", "HANDOVER_ACCEPTANCE")),
            Map.entry("acceptance_record.result", Set.of("PASS", "FAIL")),
            Map.entry("maintenance_report.status", Set.of("DRAFT", "FINAL")));

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Environment environment;

    private record ColumnInfo(String type, boolean nullable) { }
    private record ForeignKey(String child, String column, String parent, String field) { }

    @Test
    void allMappingsMatchTheFrozenPostgresqlMetadata() throws IOException {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        var entities = entityManager.getMetamodel().getEntities();
        assertThat(entities).hasSize(14);
        Map<String, Class<?>> classes = new TreeMap<>();
        for (var entity : entities) {
            Class<?> javaClass = entity.getJavaType();
            Table table = javaClass.getAnnotation(Table.class);
            assertThat(table).as(javaClass.getName() + " must have explicit @Table").isNotNull();
            assertThat(classes.put(table.name(), javaClass)).as("duplicate table mapping").isNull();
        }
        assertThat(classes.keySet()).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

        Map<String, Map<String, ColumnInfo>> databaseColumns = new TreeMap<>();
        jdbc.query("""
                SELECT table_name, column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                ORDER BY table_name, ordinal_position
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> databaseColumns
                .computeIfAbsent(rs.getString(1), ignored -> new TreeMap<>())
                .put(rs.getString(2), new ColumnInfo(rs.getString(3), "YES".equals(rs.getString(4)))));
        assertThat(databaseColumns.keySet()).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

        Map<String, ForeignKey> databaseForeignKeys = new TreeMap<>();
        jdbc.query("""
                SELECT tc.table_name, kcu.column_name, ccu.table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
                WHERE tc.table_schema = 'public' AND tc.constraint_type = 'FOREIGN KEY'
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    String child = rs.getString(1), column = rs.getString(2), parent = rs.getString(3);
                    databaseForeignKeys.put(child + "." + column, new ForeignKey(child, column, parent, ""));
                });
        assertThat(databaseForeignKeys).hasSize(31);

        Map<String, ForeignKey> mappedForeignKeys = new TreeMap<>();
        List<String> tableRows = new ArrayList<>();
        Map<String, Set<String>> mappedEnums = new TreeMap<>();
        Set<String> versions = new TreeSet<>();
        int mappedColumnCount = 0;
        for (var entry : classes.entrySet()) {
            String table = entry.getKey();
            Class<?> javaClass = entry.getValue();
            Map<String, Field> mappedColumns = new HashMap<>();
            for (Field field : javaClass.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                Column column = field.getAnnotation(Column.class);
                JoinColumn join = field.getAnnotation(JoinColumn.class);
                assertThat((column != null) ^ (join != null))
                        .as(table + "." + field.getName() + " must map one column").isTrue();
                String name = column != null ? column.name() : join.name();
                assertThat(mappedColumns.put(name, field)).as(table + "." + name + " mapped twice").isNull();
                ColumnInfo db = databaseColumns.get(table).get(name);
                assertThat(db).as("missing DB column " + table + "." + name).isNotNull();
                boolean nullable = column != null ? column.nullable() : join.nullable();
                assertThat(nullable).as("nullability of " + table + "." + name).isEqualTo(db.nullable());
                if (join != null) {
                    assertThat(field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class))
                            .as("association for " + table + "." + name).isTrue();
                    if (field.isAnnotationPresent(ManyToOne.class)) {
                        ManyToOne relationship = field.getAnnotation(ManyToOne.class);
                        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
                        assertThat(relationship.cascade()).isEmpty();
                        assertThat(relationship.optional()).isEqualTo(db.nullable());
                    } else {
                        OneToOne relationship = field.getAnnotation(OneToOne.class);
                        assertThat(relationship.fetch()).isEqualTo(FetchType.LAZY);
                        assertThat(relationship.cascade()).isEmpty();
                        assertThat(relationship.optional()).isEqualTo(db.nullable());
                    }
                    String parent = field.getType().getAnnotation(Table.class).name();
                    mappedForeignKeys.put(table + "." + name,
                            new ForeignKey(table, name, parent, field.getName()));
                    assertThat(db.type()).isEqualTo("bigint");
                } else {
                    if (field.isAnnotationPresent(Id.class)) {
                        assertThat(name).isEqualTo("id");
                        assertThat(field.getAnnotation(GeneratedValue.class).strategy())
                                .isEqualTo(GenerationType.IDENTITY);
                    }
                    assertScalarType(field, db.type(), table + "." + name);
                    if (field.isAnnotationPresent(Enumerated.class)) {
                        assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
                        mappedEnums.put(table + "." + name, Arrays.stream(field.getType().getEnumConstants())
                                .map(Object::toString).collect(Collectors.toCollection(TreeSet::new)));
                    }
                    if (field.isAnnotationPresent(Version.class)) versions.add(table + "." + name);
                }
            }
            assertThat(mappedColumns.keySet()).as("all columns of " + table)
                    .containsExactlyInAnyOrderElementsOf(databaseColumns.get(table).keySet());
            int fkCount = (int) mappedColumns.values().stream().filter(f -> f.isAnnotationPresent(JoinColumn.class)).count();
            long dbFkCount = databaseForeignKeys.values().stream().filter(fk -> fk.child().equals(table)).count();
            tableRows.add("| `" + table + "` | " + databaseColumns.get(table).size() + " | "
                    + mappedColumns.size() + " | " + dbFkCount + " | " + fkCount + " | PASS |");
            mappedColumnCount += mappedColumns.size();
        }
        assertThat(mappedColumnCount).isEqualTo(117);
        assertThat(mappedForeignKeys).hasSize(31);
        assertThat(mappedForeignKeys.keySet()).containsExactlyInAnyOrderElementsOf(databaseForeignKeys.keySet());
        for (var entry : databaseForeignKeys.entrySet()) {
            assertThat(mappedForeignKeys.get(entry.getKey()).parent())
                    .as(entry.getKey() + " parent table").isEqualTo(entry.getValue().parent());
        }
        assertThat(versions).containsExactlyInAnyOrder("maintenance_plan.version", "maintenance_plan_item.version");
        assertThat(mappedEnums.keySet()).containsExactlyInAnyOrderElementsOf(ENUM_CHECKS.keySet());
        List<String> enumRows = new ArrayList<>();
        for (var entry : ENUM_CHECKS.entrySet()) {
            String check = jdbc.queryForObject(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ? AND connamespace = 'public'::regnamespace",
                    String.class, entry.getValue());
            assertThat(check).as("CHECK " + entry.getValue()).isNotNull();
            Matcher matcher = Pattern.compile("'([^']+)'::text").matcher(check);
            Set<String> databaseValues = new TreeSet<>();
            while (matcher.find()) databaseValues.add(matcher.group(1));
            assertThat(databaseValues).as(entry.getKey() + " PostgreSQL CHECK vs frozen dictionary")
                    .containsExactlyInAnyOrderElementsOf(FROZEN_ENUMS.get(entry.getKey()));
            assertThat(mappedEnums.get(entry.getKey())).as(entry.getKey() + " Java enum vs frozen dictionary")
                    .containsExactlyInAnyOrderElementsOf(FROZEN_ENUMS.get(entry.getKey()));
            enumRows.add("| `" + entry.getKey() + "` | " + databaseValues.size() + " | PASS |");
        }
        assertThat(mappedEnums.get("maintenance_plan.status")).hasSize(8);
        assertThat(mappedEnums.get("maintenance_plan_item.status")).hasSize(11);

        StringBuilder audit = new StringBuilder("# Phase 2.2 Mapping Audit\n\n"
                + "Automated comparison of the JPA metamodel and annotations with PostgreSQL `public` metadata. "
                + "Run `mvn -f backend/pom.xml test` with the Phase 2.1 development database to regenerate.\n\n"
                + "**Result: PASS — 14 entities / 14 tables; 117 / 117 columns; 31 / 31 FK associations; "
                + "11 / 11 constrained enums; 2 / 2 version fields.**\n\n"
                + "## Table and column coverage\n\n"
                + "| Table | DB columns | Mapped fields | DB FKs | Association fields | Result |\n"
                + "| --- | ---: | ---: | ---: | ---: | --- |\n");
        tableRows.forEach(row -> audit.append(row).append('\n'));
        audit.append("\n## Foreign key ownership\n\n"
                + "| Database FK | Child entity field | Parent table | Result |\n"
                + "| --- | --- | --- | --- |\n");
        for (var fk : mappedForeignKeys.values()) {
            audit.append("| `").append(fk.child()).append('.').append(fk.column())
                    .append("` | `").append(classes.get(fk.child()).getSimpleName()).append('.')
                    .append(fk.field()).append("` | `").append(fk.parent()).append("` | PASS |\n");
        }
        audit.append("\n## Constrained enums\n\n| Column | Values | Result |\n| --- | ---: | --- |\n");
        enumRows.stream().sorted().forEach(row -> audit.append(row).append('\n'));
        audit.append("\n`@Version`: `maintenance_plan.version`, `maintenance_plan_item.version` only. "
                + "No removed table or Flyway metadata table is mapped.\n");
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path projectRoot = cwd.getFileName().toString().equals("backend") ? cwd.getParent() : cwd;
        Path output = projectRoot.resolve("reports/backend/phase_2_2_mapping_audit.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, audit.toString());
    }

    private static void assertScalarType(Field field, String dbType, String label) {
        Class<?> javaType = field.getType();
        boolean matches = switch (dbType) {
            case "bigint" -> javaType == Long.class;
            case "integer" -> javaType == Integer.class;
            case "boolean" -> javaType == Boolean.class;
            case "date" -> javaType == LocalDate.class;
            case "timestamp with time zone" -> javaType == OffsetDateTime.class;
            case "text" -> javaType == String.class || javaType.isEnum();
            default -> false;
        };
        assertThat(matches).as(label + " SQL " + dbType + " -> " + javaType.getSimpleName()).isTrue();
    }
}
