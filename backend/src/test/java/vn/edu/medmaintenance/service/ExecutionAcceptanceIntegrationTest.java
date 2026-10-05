package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecutionAcceptanceIntegrationTest {
    private java.util.Map<Long,String> fixtureStatuses;
    @org.junit.jupiter.api.BeforeEach void isolateOpenFixturePlans() { fixtureStatuses=PlanningTestData.archiveFixturePlans(jdbc); }
    @org.junit.jupiter.api.AfterEach void restoreOpenFixturePlans() { PlanningTestData.restoreFixturePlans(jdbc,fixtureStatuses); }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean WorkflowHistory history;
    private final List<Long> plans = new ArrayList<>();
    private String vtyt, director, department, admin;
    private long freeEq, paidEq, freeCoverage, paidCoverage, provider, vtytId;

    @BeforeAll
    void setup() {
        vtyt = login("demo_vtyt", "VTYT");
        director = login("demo_bgd", "BGD");
        department = login("demo_khoa_noi", "KHOA");
        admin = login("demo_admin", "ADMIN");
        freeEq = equipment("DEMO-EQ-001");
        paidEq = equipment("DEMO-EQ-002");
        freeCoverage = coverage(freeEq);
        paidCoverage = coverage(paidEq);
        provider = jdbc.queryForObject("SELECT id FROM service_provider WHERE active=true ORDER BY id LIMIT 1", Long.class);
        vtytId = jdbc.queryForObject("SELECT id FROM user_account WHERE username='demo_vtyt'", Long.class);
    }

    @AfterEach
    void cleanup() {
        reset(history);
        for (long plan : plans) {
            PlanningTestData.cleanNotifications(jdbc,plan);
            jdbc.update("DELETE FROM acceptance_record WHERE execution_id IN (SELECT e.id FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id=?)", plan);
            jdbc.update("DELETE FROM maintenance_progress_log WHERE execution_id IN (SELECT e.id FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id=?)", plan);
            jdbc.update("DELETE FROM maintenance_execution WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", plan);
            jdbc.update("DELETE FROM approval_action WHERE request_id IN (SELECT r.id FROM approval_request r WHERE r.plan_id=? OR r.plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?))", plan, plan);
            jdbc.update("DELETE FROM approval_request WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", plan, plan);
            jdbc.update("DELETE FROM status_history WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", plan);
            jdbc.update("DELETE FROM status_history WHERE plan_id=?", plan);
            jdbc.update("DELETE FROM maintenance_plan_item WHERE plan_id=?", plan);
            jdbc.update("DELETE FROM maintenance_plan WHERE id=?", plan);
        }
        plans.clear();
        assertThat(count("maintenance_plan")).isEqualTo(8);
        assertThat(count("maintenance_execution")).isEqualTo(30);
        assertThat(count("maintenance_progress_log")).isEqualTo(90);
        assertThat(count("acceptance_record")).isEqualTo(40);
        assertThat(count("status_history")).isEqualTo(240);
    }

    @Test
    void contractStartProgressTechnicalAndHandoverCompletePlan() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        int planVersion = planVersion(plan), itemVersion = version(item);
        fail(send("/api/plan-items/" + item + "/executions", vtyt,
                Map.of("version", itemVersion, "planVersion", planVersion - 1)), 409, "OPTIMISTIC_LOCK_CONFLICT");
        JsonNode started = start(plan, item);
        long execution = started.path("executionId").asLong();
        assertThat(started.path("attemptNo").asInt()).isEqualTo(1);
        assertThat(started.path("planStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(started.path("providerId").asLong()).isEqualTo(
                jdbc.queryForObject("SELECT provider_id FROM maintenance_coverage WHERE id=?", Long.class, freeCoverage));
        assertThat(states("plan_id", plan)).containsExactly("DRAFT", "SUBMITTED", "APPROVED", "IN_PROGRESS");
        fail(send("/api/plan-items/" + item + "/executions", vtyt,
                Map.of("version", itemVersion, "planVersion", planVersion)), 409, "OPTIMISTIC_LOCK_CONFLICT");
        JsonNode log = ok(send("/api/executions/" + execution + "/progress", vtyt,
                Map.of("workNote", "Đã kiểm tra tình trạng thiết bị")), 201);
        assertThat(log.path("progressId").asLong()).isPositive();
        ok(send("/api/executions/" + execution + "/progress", vtyt,
                Map.of("workNote", "Đã kiểm tra lại thông số")), 201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_progress_log WHERE execution_id=?", Integer.class, execution)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT work_note FROM maintenance_progress_log WHERE execution_id=? ORDER BY event_at,id",
                String.class, execution)).containsExactly("Đã kiểm tra tình trạng thiết bị", "Đã kiểm tra lại thông số");
        JsonNode finished = finish(execution, item);
        assertThat(finished.path("itemStatus").asText()).isEqualTo("AWAITING_TECHNICAL_ACCEPTANCE");
        fail(send("/api/executions/" + execution + "/handover", department,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Đạt", "vtytSignerId", vtytId)),
                409, "HANDOVER_NOT_ALLOWED");
        JsonNode accepted = technical(execution, item, "PASS", false);
        assertThat(accepted.path("itemStatus").asText()).isEqualTo("AWAITING_HANDOVER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification n JOIN user_account u ON u.id=n.user_account_id WHERE n.notification_type='HANDOVER_PENDING' AND n.target_url=? AND (u.role_code<>'KHOA_PHONG' OR u.department_id<>(SELECT department_id_at_plan FROM maintenance_plan_item WHERE id=?) OR u.active=false)",Integer.class,"/plans/"+plan+"/items/"+item+"/execution",item)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE notification_type='HANDOVER_PENDING' AND target_url=?",Integer.class,"/plans/"+plan+"/items/"+item+"/execution")).isGreaterThan(0);
        JsonNode handed = handover(execution, item, "PASS", false);
        assertThat(handed.path("itemStatus").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE notification_type='HANDOVER_COMPLETED' AND target_url=?",Integer.class,"/plans/"+plan+"/items/"+item+"/execution")).isGreaterThan(0);
        assertThat(handed.path("planStatus").asText()).isEqualTo("AWAITING_REPORT");
        assertThat(jdbc.queryForObject("SELECT department_confirmed_by_user_id IS NOT NULL AND vtyt_confirmed_by_user_id=? FROM acceptance_record WHERE id=?", Boolean.class, vtytId, handed.path("acceptanceId").asLong())).isTrue();
        assertThat(states("plan_item_id", item)).containsExactly("PLANNED", "UNDER_CONTRACT", "IN_MAINTENANCE",
                "AWAITING_TECHNICAL_ACCEPTANCE", "AWAITING_HANDOVER", "COMPLETED");
        fail(send("/api/executions/" + execution + "/handover", department,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Lặp", "vtytSignerId", vtytId)),
                409, "PLAN_NOT_EXECUTABLE");
    }

    @Test
    void predefinedProgressAppendsOptionalNotesAndStopsAtTechnicalCompletion() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        long execution = start(plan, item).path("executionId").asLong();
        String endpoint = "/api/executions/" + execution + "/progress";
        for (String token : List.of(director, department, admin))
            fail(send(endpoint, token, Map.of("status", "IN_PROGRESS", "version", version(item))), 403, "ACCESS_DENIED");
        fail(send(endpoint, vtyt, Map.of("status", "IN_PROGRESS", "version", version(item)-1)), 409, "OPTIMISTIC_LOCK_CONFLICT");
        fail(send(endpoint, vtyt, Map.of("status", "ARBITRARY_STATUS")), 400, "INVALID_PARAMETER");
        ok(send(endpoint, vtyt, Map.of("status", "IN_PROGRESS", "version", version(item))), 201);
        ok(send(endpoint, vtyt, Map.of("status", "PAUSED", "note", "Tạm ngừng kiểm tra", "version", version(item))), 201);
        ok(send(endpoint, vtyt, Map.of("status", "WAITING_PARTS", "note", "Chờ bộ lọc", "version", version(item))), 201);
        ok(send(endpoint, vtyt, Map.of("status", "WAITING_PROVIDER", "version", version(item))), 201);
        ok(send(endpoint, vtyt, Map.of("status", "WORK_DONE", "version", version(item))), 201);
        assertThat(jdbc.queryForList("SELECT work_note FROM maintenance_progress_log WHERE execution_id=? ORDER BY event_at,id",
                String.class, execution)).containsExactly("Đang bảo trì", "Tạm dừng\nTạm ngừng kiểm tra",
                        "Chờ linh kiện\nChờ bộ lọc", "Chờ đơn vị bảo trì", "Bảo trì xong");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_progress_log WHERE execution_id=? AND recorded_by_user_id=?",
                Integer.class, execution, vtytId)).isEqualTo(5);
        // WORK_DONE is an informational update. Completion requires its explicit command.
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan_item WHERE id=?", String.class, item)).isEqualTo("IN_MAINTENANCE");
        assertThat(finish(execution, item).path("itemStatus").asText()).isEqualTo("AWAITING_TECHNICAL_ACCEPTANCE");
        fail(send(endpoint, vtyt, Map.of("status", "IN_PROGRESS", "version", version(item))), 409, "EXECUTION_NOT_ACTIVE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_progress_log WHERE execution_id=?", Integer.class, execution)).isEqualTo(5);
    }

    @Test
    void externalRouteAndTechnicalReworkRetainAttempts() {
        long plan = approvedPlan(paidEq);
        long item = routeExternal(plan, paidEq);
        JsonNode first = start(plan, item);
        long execution1 = first.path("executionId").asLong();
        assertThat(first.path("providerId").asLong()).isEqualTo(provider);
        finish(execution1, item);
        JsonNode failed = technical(execution1, item, "FAIL", false);
        assertThat(failed.path("itemStatus").asText()).isEqualTo("REWORK_REQUIRED");
        JsonNode second = start(plan, item);
        long execution2 = second.path("executionId").asLong();
        assertThat(second.path("attemptNo").asInt()).isEqualTo(2);
        assertThat(execution2).isNotEqualTo(execution1);
        fail(send("/api/executions/" + execution1 + "/progress", vtyt,
                Map.of("workNote", "late")), 409, "EXECUTION_NOT_CURRENT");
        fail(send("/api/executions/" + execution1 + "/technical-acceptance", vtyt,
                Map.of("version", version(item), "result", "PASS", "conclusion", "late")),
                409, "EXECUTION_NOT_CURRENT");
        finish(execution2, item);
        technical(execution2, item, "PASS", false);
        handover(execution2, item, "PASS", false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_execution WHERE plan_item_id=?", Integer.class, item)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE execution_id=?", Integer.class, execution1)).isEqualTo(1);
    }

    @Test
    void handoverFailureReworkAndRepairHandoff() {
        long plan = approvedPlan(freeEq, paidEq);
        long firstItem = routeFree(plan, freeEq);
        long secondItem = routeExternal(plan, paidEq);
        long first = start(plan, firstItem).path("executionId").asLong();
        finish(first, firstItem);
        technical(first, firstItem, "PASS", false);
        JsonNode failed = handover(first, firstItem, "FAIL", false);
        assertThat(failed.path("itemStatus").asText()).isEqualTo("REWORK_REQUIRED");
        long again = start(plan, firstItem).path("executionId").asLong();
        finish(again, firstItem);
        technical(again, firstItem, "PASS", false);
        handover(again, firstItem, "PASS", false);
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan WHERE id=?", String.class, plan)).isEqualTo("IN_PROGRESS");
        long second = start(plan, secondItem).path("executionId").asLong();
        JsonNode repair = ok(send("/api/executions/" + second + "/repair-required", vtyt,
                Map.of("version", version(secondItem), "reason", "Hư hỏng cần xử lý sửa chữa")), 200);
        assertThat(repair.path("itemStatus").asText()).isEqualTo("REPAIR_REQUIRED");
        assertThat(repair.path("planStatus").asText()).isEqualTo("AWAITING_REPORT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_progress_log WHERE execution_id=? AND damage_note IS NOT NULL", Integer.class, second)).isEqualTo(1);
        fail(send("/api/plan-items/" + secondItem + "/executions", vtyt,
                Map.of("version", version(secondItem), "planVersion", planVersion(plan))), 409, "PLAN_NOT_EXECUTABLE");
    }

    @Test
    void invalidEvidenceAndRolesCannotCreateAttempt() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        var command = Map.of("version", version(item), "planVersion", planVersion(plan));
        fail(send("/api/plan-items/" + item + "/executions", null, command), 401, "AUTHENTICATION_REQUIRED");
        for (String token : List.of(director, department, admin))
            fail(send("/api/plan-items/" + item + "/executions", token, command), 403, "ACCESS_DENIED");
        long original = jdbc.queryForObject("SELECT assigned_provider_id FROM maintenance_plan_item WHERE id=?", Long.class, item);
        long actual = jdbc.queryForObject("SELECT provider_id FROM maintenance_coverage WHERE id=?", Long.class, freeCoverage);
        long wrongProvider = jdbc.queryForObject(
                "SELECT id FROM service_provider WHERE active=true AND id<>? ORDER BY id LIMIT 1", Long.class, actual);
        jdbc.update("UPDATE maintenance_plan_item SET assigned_provider_id=? WHERE id=?", wrongProvider, item);
        fail(send("/api/plan-items/" + item + "/executions", vtyt, command), 409, "INVALID_PROVIDER_ROUTE");
        jdbc.update("UPDATE maintenance_plan_item SET assigned_provider_id=? WHERE id=?", original, item);
        jdbc.update("UPDATE maintenance_plan_item SET assignment_route='EXTERNAL_APPROVED' WHERE id=?", item);
        try {
            fail(send("/api/plan-items/" + item + "/executions", vtyt, command), 409, "INVALID_PROVIDER_ROUTE");
        } finally {
            jdbc.update("UPDATE maintenance_plan_item SET assignment_route='UNDER_CONTRACT' WHERE id=?", item);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_execution WHERE plan_item_id=?", Integer.class, item)).isZero();
    }

    @Test
    void signerScopeDuplicateAndRollbackAreSafe() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        long execution = start(plan, item).path("executionId").asLong();
        finish(execution, item);
        fail(send("/api/executions/" + execution + "/technical-acceptance", vtyt,
                Map.of("version", version(item), "result", "PASS", "conclusion", "  ")),
                400, "VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE execution_id=?", Integer.class, execution)).isZero();
        fail(send("/api/executions/" + execution + "/technical-acceptance", director,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Đạt")),
                403, "ACCESS_DENIED");
        doThrow(new IllegalStateException("test-only history failure")).when(history).itemTransition(
                any(), any(), eq("AWAITING_TECHNICAL_ACCEPTANCE"), eq("AWAITING_HANDOVER"),
                eq("TECHNICAL_ACCEPTANCE"), isNull(), any());
        try {
            fail(send("/api/executions/" + execution + "/technical-acceptance", vtyt,
                    Map.of("version", version(item), "result", "PASS", "conclusion", "Đạt")),
                    500, "INTERNAL_ERROR");
        } finally { reset(history); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE execution_id=?", Integer.class, execution)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan_item WHERE id=?", String.class, item)).isEqualTo("AWAITING_TECHNICAL_ACCEPTANCE");
        technical(execution, item, "PASS", false);
        fail(send("/api/executions/" + execution + "/technical-acceptance", vtyt,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Lặp")),
                409, "TECHNICAL_ACCEPTANCE_EXISTS");
        fail(send("/api/executions/" + execution + "/handover", department,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Đạt")),
                400, "HANDOVER_SIGNER_REQUIRED");
        HttpHeaders wrongCosigner = new HttpHeaders();
        wrongCosigner.setBearerAuth(department);
        wrongCosigner.set("X-VTYT-Authorization", "Bearer " + admin);
        wrongCosigner.set("Content-Type", "application/json");
        fail(http.exchange("/api/executions/" + execution + "/handover", HttpMethod.POST,
                new HttpEntity<>(Map.of("version", version(item), "result", "PASS",
                        "conclusion", "Đạt"), wrongCosigner), JsonNode.class),
                403, "HANDOVER_SIGNER_INVALID");
        long wrongDepartment = jdbc.queryForObject("SELECT id FROM department WHERE code<>'KHOA_NOI' ORDER BY id LIMIT 1", Long.class);
        long originalDepartment = jdbc.queryForObject("SELECT department_id FROM user_account WHERE username='demo_khoa_noi'", Long.class);
        jdbc.update("UPDATE user_account SET department_id=? WHERE username='demo_khoa_noi'", wrongDepartment);
        try {
            fail(send("/api/executions/" + execution + "/handover", department,
                    Map.of("version", version(item), "result", "PASS", "conclusion", "Đạt", "vtytSignerId", vtytId)),
                    403, "DEPARTMENT_SCOPE_VIOLATION");
        } finally {
            jdbc.update("UPDATE user_account SET department_id=? WHERE username='demo_khoa_noi'", originalDepartment);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE execution_id=? AND acceptance_type='HANDOVER_ACCEPTANCE'", Integer.class, execution)).isZero();
    }

    @Test
    void laterItemStartDoesNotDuplicatePlanTransition() {
        long otherFree = jdbc.queryForObject("""
                SELECT e.id FROM equipment e JOIN maintenance_coverage c ON c.equipment_id=e.id
                WHERE c.classification='FREE' AND e.id<>? ORDER BY e.id LIMIT 1
                """, Long.class, freeEq);
        long plan = approvedPlan(freeEq, otherFree);
        long first = routeFree(plan, freeEq);
        long second = routeFree(plan, otherFree);
        start(plan, first);
        int afterFirst = planVersion(plan);
        start(plan, second);
        assertThat(planVersion(plan)).isEqualTo(afterFirst);
        assertThat(states("plan_id", plan)).containsExactly("DRAFT", "SUBMITTED", "APPROVED", "IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id=?", Integer.class, plan)).isEqualTo(2);
    }

    @Test
    void technicalAndHandoverRepairDecisionsPreserveFailureEvidence() {
        long plan = approvedPlan(freeEq, paidEq);
        long firstItem = routeFree(plan, freeEq);
        long secondItem = routeExternal(plan, paidEq);
        long first = start(plan, firstItem).path("executionId").asLong();
        finish(first, firstItem);
        JsonNode technicalRepair = technical(first, firstItem, "FAIL", true);
        assertThat(technicalRepair.path("itemStatus").asText()).isEqualTo("REPAIR_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT reason FROM status_history WHERE plan_item_id=? AND new_state='REPAIR_REQUIRED' ORDER BY id DESC LIMIT 1", String.class, firstItem)).isNotBlank();
        long second = start(plan, secondItem).path("executionId").asLong();
        finish(second, secondItem);
        technical(second, secondItem, "PASS", false);
        JsonNode handoverRepair = handover(second, secondItem, "FAIL", true);
        assertThat(handoverRepair.path("itemStatus").asText()).isEqualTo("REPAIR_REQUIRED");
        assertThat(handoverRepair.path("planStatus").asText()).isEqualTo("AWAITING_REPORT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE result='FAIL' AND execution_id IN (?,?)", Integer.class, first, second)).isEqualTo(2);
    }

    @Test
    void staleVersionsAndWrongStatesLeaveNoPartialEvidence() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        fail(send("/api/plan-items/" + item + "/executions", vtyt,
                Map.of("version", version(item)-1, "planVersion", planVersion(plan))), 409, "OPTIMISTIC_LOCK_CONFLICT");
        long execution = start(plan, item).path("executionId").asLong();
        fail(send("/api/executions/" + execution + "/complete-work", vtyt,
                Map.of("version", version(item)-1)), 409, "OPTIMISTIC_LOCK_CONFLICT");
        fail(send("/api/executions/" + execution + "/technical-acceptance", vtyt,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Too early")),
                409, "EXECUTION_NOT_READY_FOR_ACCEPTANCE");
        finish(execution, item);
        fail(send("/api/executions/" + execution + "/technical-acceptance", vtyt,
                Map.of("version", version(item)-1, "result", "PASS", "conclusion", "Stale")),
                409, "OPTIMISTIC_LOCK_CONFLICT");
        technical(execution, item, "PASS", false);
        fail(send("/api/executions/" + execution + "/handover", department,
                Map.of("version", version(item)-1, "result", "PASS", "conclusion", "Stale", "vtytSignerId", vtytId)),
                409, "OPTIMISTIC_LOCK_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE execution_id=?", Integer.class, execution)).isEqualTo(1);
    }

    @Test
    void externalApprovalMustRemainValidAtExecutionStart() {
        long plan = approvedPlan(paidEq);
        long item = routeExternal(plan, paidEq);
        long request = jdbc.queryForObject("""
                SELECT id FROM approval_request WHERE plan_item_id=? AND request_type='VENDOR_SELECTION'
                ORDER BY id DESC LIMIT 1
                """, Long.class, item);
        long anotherProvider = jdbc.queryForObject(
                "SELECT id FROM service_provider WHERE active=true AND id<>? ORDER BY id LIMIT 1", Long.class, provider);
        jdbc.update("UPDATE approval_request SET proposed_provider_id=? WHERE id=?", anotherProvider, request);
        try {
            fail(send("/api/plan-items/" + item + "/executions", vtyt,
                    Map.of("version", version(item), "planVersion", planVersion(plan))),
                    409, "INVALID_PROVIDER_ROUTE");
        } finally {
            jdbc.update("UPDATE approval_request SET proposed_provider_id=? WHERE id=?", provider, request);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_execution WHERE plan_item_id=?", Integer.class, item)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan WHERE id=?", String.class, plan)).isEqualTo("APPROVED");
    }

    @Test
    void duplicateAssessmentsAndOldAttemptPassAreRejected() {
        long otherFree = jdbc.queryForObject("""
                SELECT e.id FROM equipment e JOIN maintenance_coverage c ON c.equipment_id=e.id
                WHERE c.classification='FREE' AND e.id<>? ORDER BY e.id LIMIT 1
                """, Long.class, freeEq);
        long plan = approvedPlan(freeEq, otherFree);
        long item = routeFree(plan, freeEq);
        routeFree(plan, otherFree);
        long first = start(plan, item).path("executionId").asLong();
        finish(first, item);
        technical(first, item, "PASS", false);
        fail(send("/api/executions/" + first + "/technical-acceptance", vtyt,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Again")),
                409, "TECHNICAL_ACCEPTANCE_EXISTS");
        handover(first, item, "FAIL", false);
        fail(send("/api/executions/" + first + "/handover", department,
                Map.of("version", version(item), "result", "FAIL", "conclusion", "Again")),
                409, "HANDOVER_ACCEPTANCE_EXISTS");
        long second = start(plan, item).path("executionId").asLong();
        finish(second, item);
        fail(send("/api/executions/" + second + "/handover", department,
                Map.of("version", version(item), "result", "PASS", "conclusion", "Old PASS", "vtytSignerId", vtytId)),
                409, "HANDOVER_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE execution_id=?", Integer.class, second)).isZero();
    }

    @Test
    void commandRolesAndMissingSignerAreRejectedWithoutSideEffects() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        long execution = start(plan, item).path("executionId").asLong();
        for (String token : List.of(director, department, admin)) {
            fail(send("/api/executions/" + execution + "/progress", token,
                    Map.of("workNote", "Denied")), 403, "ACCESS_DENIED");
            fail(send("/api/executions/" + execution + "/complete-work", token,
                    Map.of("version", version(item))), 403, "ACCESS_DENIED");
            fail(send("/api/executions/" + execution + "/repair-required", token,
                    Map.of("version", version(item), "reason", "Denied")), 403, "ACCESS_DENIED");
        }
        fail(send("/api/executions/" + execution + "/progress", null,
                Map.of("workNote", "Denied")), 401, "AUTHENTICATION_REQUIRED");
        finish(execution, item);
        for (String token : List.of(director, department, admin))
            fail(send("/api/executions/" + execution + "/technical-acceptance", token,
                    Map.of("version", version(item), "result", "PASS", "conclusion", "Denied")),
                    403, "ACCESS_DENIED");
        technical(execution, item, "PASS", false);
        for (String token : List.of(vtyt, director, admin))
            fail(send("/api/executions/" + execution + "/handover", token,
                    Map.of("version", version(item), "result", "PASS", "conclusion", "Denied",
                            "vtytSignerId", vtytId)), 403, "ACCESS_DENIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acceptance_record WHERE execution_id=? AND acceptance_type='HANDOVER_ACCEPTANCE'", Integer.class, execution)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_progress_log WHERE execution_id=?", Integer.class, execution)).isZero();
    }

    @Test
    void wrongPlanAndUnroutedItemCannotStart() {
        long plan = approvedPlan(freeEq);
        long item = item(plan, freeEq);
        jdbc.update("UPDATE maintenance_plan_item SET status='PLANNED',assigned_provider_id=null,assignment_route=null WHERE id=?",item);
        fail(send("/api/plan-items/" + item + "/executions", vtyt,
                Map.of("version", version(item), "planVersion", planVersion(plan))),
                409, "PLAN_ITEM_NOT_EXECUTABLE");
        jdbc.update("UPDATE maintenance_plan_item SET status='UNDER_CONTRACT',assigned_provider_id=(SELECT provider_id FROM maintenance_coverage WHERE id=coverage_id),assignment_route='UNDER_CONTRACT' WHERE id=?",item);
        jdbc.update("UPDATE maintenance_plan SET status='SUBMITTED' WHERE id=?", plan);
        try {
            fail(send("/api/plan-items/" + item + "/executions", vtyt,
                    Map.of("version", version(item), "planVersion", planVersion(plan))),
                    409, "PLAN_NOT_EXECUTABLE");
        } finally {
            jdbc.update("UPDATE maintenance_plan SET status='APPROVED' WHERE id=?", plan);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_execution WHERE plan_item_id=?", Integer.class, item)).isZero();
    }

    @Test
    void handoverUsesHistoricalDepartmentWhenEquipmentMoves() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        long execution = start(plan, item).path("executionId").asLong();
        finish(execution, item);
        technical(execution, item, "PASS", false);
        long original = jdbc.queryForObject("SELECT department_id FROM equipment WHERE id=?", Long.class, freeEq);
        long other = jdbc.queryForObject("SELECT id FROM department WHERE id<>? ORDER BY id LIMIT 1", Long.class, original);
        jdbc.update("UPDATE equipment SET department_id=? WHERE id=?", other, freeEq);
        try {
            assertThat(handover(execution, item, "PASS", false).path("itemStatus").asText()).isEqualTo("COMPLETED");
        } finally {
            jdbc.update("UPDATE equipment SET department_id=? WHERE id=?", original, freeEq);
        }
    }

    private long approvedPlan(long... equipmentIds) {
        List<Map<String, Object>> selections = new ArrayList<>();
        for (long id : equipmentIds) selections.add(PlanningTestData.complete(jdbc,id));
        JsonNode created = ok(send("/api/plans", vtyt,
                Map.of("title", "TEST-EXECUTION-" + UUID.randomUUID(), "periodStart", "2026-11-01",
                        "periodEnd", "2026-11-30", "items", selections)), 201);
        long plan = created.path("id").asLong();
        plans.add(plan);
        JsonNode submitted = ok(send("/api/plans/" + plan + "/submit", vtyt,
                Map.of("version", created.path("version").asInt())), 200);
        ok(send("/api/approvals/" + submitted.path("approvalRequestId").asLong() + "/decision", director,
                Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE")), 200);
        return plan;
    }

    private long routeFree(long plan, long equipmentId) {
        long item = item(plan, equipmentId);
        return item;
    }

    private long routeExternal(long plan, long equipmentId) {
        long item = item(plan, equipmentId);
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan_item WHERE id=?",String.class,item)).isEqualTo("ASSIGNED_EXTERNAL");
        return item;
    }

    private JsonNode start(long plan, long item) {
        return ok(send("/api/plan-items/" + item + "/executions", vtyt,
                Map.of("version", version(item), "planVersion", planVersion(plan))), 201);
    }
    private JsonNode finish(long execution, long item) {
        return ok(send("/api/executions/" + execution + "/complete-work", vtyt,
                Map.of("version", version(item), "resultNote", "Công việc kỹ thuật kết thúc")), 200);
    }
    private JsonNode technical(long execution, long item, String result, boolean repair) {
        return ok(send("/api/executions/" + execution + "/technical-acceptance", vtyt,
                Map.of("version", version(item), "result", result, "conclusion", result.equals("PASS") ? "Đạt" : "Cần làm lại",
                        "repairRequired", repair)), 201);
    }
    private JsonNode handover(long execution, long item, String result, boolean repair) {
        JsonNode response = ok(send("/api/executions/" + execution + "/handover", department,
                Map.of("version", version(item), "result", result, "conclusion", result.equals("PASS") ? "Đạt" : "Cần làm lại",
                        "vtytSignerId", vtytId, "repairRequired", repair)), 201);
        long plan = jdbc.queryForObject("SELECT plan_id FROM maintenance_plan_item WHERE id=?", Long.class, item);
        String type = result.equals("PASS") ? "HANDOVER_COMPLETED" : "HANDOVER_REWORK";
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE notification_type=? AND target_url=?",
                Integer.class, type, "/plans/" + plan + "/items/" + item + "/execution")).isGreaterThan(0);
        return response;
    }
    private long equipment(String code) {
        return jdbc.queryForObject("SELECT id FROM equipment WHERE equipment_code=?", Long.class, code);
    }
    private long coverage(long equipmentId) {
        return jdbc.queryForObject("SELECT id FROM maintenance_coverage WHERE equipment_id=? ORDER BY id LIMIT 1", Long.class, equipmentId);
    }
    private long item(long plan, long equipmentId) {
        return jdbc.queryForObject("SELECT id FROM maintenance_plan_item WHERE plan_id=? AND equipment_id=?", Long.class, plan, equipmentId);
    }
    private int version(long item) {
        return jdbc.queryForObject("SELECT version FROM maintenance_plan_item WHERE id=?", Integer.class, item);
    }
    private int planVersion(long plan) {
        return jdbc.queryForObject("SELECT version FROM maintenance_plan WHERE id=?", Integer.class, plan);
    }
    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
    private List<String> states(String column, long id) {
        return jdbc.queryForList("SELECT new_state FROM status_history WHERE " + column + "=? ORDER BY id", String.class, id);
    }
    private String login(String name, String role) {
        String password = System.getenv("DEMO_" + role + "_PASSWORD");
        assertThat(password).isNotBlank();
        return ok(send("/api/auth/login", null, Map.of("username", name, "password", password)), 200)
                .path("accessToken").asText();
    }
    private ResponseEntity<JsonNode> send(String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        Object requestBody = body;
        if (path.endsWith("/handover") && body instanceof Map<?, ?> map
                && map.containsKey("vtytSignerId")) {
            headers.set("X-VTYT-Authorization", "Bearer " + vtyt);
            java.util.Map<String, Object> copy = new java.util.HashMap<>();
            map.forEach((key, value) -> copy.put(key.toString(), value));
            copy.remove("vtytSignerId");
            requestBody = copy;
        }
        if (requestBody != null) headers.set("Content-Type", "application/json");
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(requestBody, headers), JsonNode.class);
    }
    private JsonNode ok(ResponseEntity<JsonNode> response, int status) {
        assertThat(response.getStatusCode().value()).as(String.valueOf(response.getBody())).isEqualTo(status);
        return response.getBody();
    }
    private void fail(ResponseEntity<JsonNode> response, int status, String code) {
        JsonNode body = ok(response, status);
        assertThat(body.path("code").asText()).isEqualTo(code);
    }
}
