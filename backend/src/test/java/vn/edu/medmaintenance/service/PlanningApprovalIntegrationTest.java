package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.medmaintenance.api.dto.request.CreatePlanRequest;
import vn.edu.medmaintenance.api.dto.request.PlanItemInput;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.enums.ApprovalOutcome;
import vn.edu.medmaintenance.api.dto.request.DecidePlanRequest;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlanningApprovalIntegrationTest {
    private java.util.Map<Long,String> fixtureStatuses;
    @org.junit.jupiter.api.BeforeEach void isolateOpenFixturePlans() { fixtureStatuses=PlanningTestData.archiveFixturePlans(jdbc); }
    @org.junit.jupiter.api.AfterEach void restoreOpenFixturePlans() { PlanningTestData.restoreFixturePlans(jdbc,fixtureStatuses); }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlanningService planning;
    @Autowired PlanApprovalService planApproval;
    private final List<Long> createdPlans = new ArrayList<>();
    private String vtyt;
    private String director;
    private String department;
    private String admin;
    private long eq1;
    private long eq2;
    private long eq3;

    @BeforeAll
    void setup() {
        vtyt = login("demo_vtyt", "VTYT");
        director = login("demo_bgd", "BGD");
        department = login("demo_khoa_noi", "KHOA");
        admin = login("demo_admin", "ADMIN");
        eq1 = id("DEMO-EQ-001");
        eq2 = id("DEMO-EQ-002");
        eq3 = id("DEMO-EQ-003");
    }

    @AfterEach
    void clean() {
        for (Long planId : createdPlans) {
            PlanningTestData.cleanNotifications(jdbc,planId);
            jdbc.update("DELETE FROM approval_action WHERE request_id IN (SELECT id FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?))",planId);
            jdbc.update("DELETE FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)",planId);
            jdbc.update("DELETE FROM status_history WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", planId);
            jdbc.update("DELETE FROM status_history WHERE plan_id=?", planId);
            jdbc.update("DELETE FROM approval_action WHERE request_id IN (SELECT id FROM approval_request WHERE plan_id=?)", planId);
            jdbc.update("DELETE FROM approval_request WHERE plan_id=?", planId);
            jdbc.update("DELETE FROM maintenance_plan_item WHERE plan_id=?", planId);
            jdbc.update("DELETE FROM maintenance_plan WHERE id=?", planId);
        }
        createdPlans.clear();
        assertThat(count("maintenance_plan")).isEqualTo(8);
        assertThat(count("maintenance_plan_item")).isEqualTo(52);
        assertThat(count("approval_request")).isEqualTo(19);
        assertThat(count("approval_action")).isEqualTo(15);
        assertThat(count("status_history")).isEqualTo(240);
    }

    @Test
    void fullRevisionRoundPreservesTwoRequestsActionsAndOrderedHistory() {
        JsonNode created = create(eq1);
        long planId = created.path("id").asLong();
        int version = created.path("version").asInt();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(success(send(HttpMethod.GET, "/api/plans/" + planId, vtyt, null), 200).path("status").asText())
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan_item WHERE plan_id=?", String.class, planId))
                .isEqualTo("UNDER_CONTRACT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM status_history WHERE plan_id=?", Integer.class, planId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM status_history WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", Integer.class, planId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT i.department_id_at_plan=e.department_id FROM maintenance_plan_item i JOIN equipment e ON e.id=i.equipment_id WHERE i.plan_id=?", Boolean.class, planId)).isTrue();

        JsonNode firstSubmit = success(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt, Map.of("version", version)), 200);
        long firstRequest = firstSubmit.path("approvalRequestId").asLong();
        assertThat(firstSubmit.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(success(send(HttpMethod.GET, "/api/plans/" + planId, vtyt, null), 200).path("status").asText())
                .isEqualTo("SUBMITTED");
        assertThat(pendingContains(firstRequest)).isTrue();
        JsonNode revision = success(send(HttpMethod.POST, "/api/approvals/" + firstRequest + "/decision", director,
                Map.of("version", firstSubmit.path("version").asInt(), "outcome", "REVISION_REQUIRED", "comment", "Cần đổi lịch")), 200);
        assertThat(revision.path("planStatus").asText()).isEqualTo("REVISION_REQUIRED");
        assertThat(pendingContains(firstRequest)).isFalse();
        error(send(HttpMethod.POST, "/api/approvals/" + firstRequest + "/decision", director,
                Map.of("version", revision.path("planVersion").asInt(), "outcome", "REVISION_REQUIRED",
                        "comment", "Lặp")), 409, "APPROVAL_REQUEST_NOT_PENDING");
        error(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt,
                Map.of("version", revision.path("planVersion").asInt())), 409, "PLAN_NOT_SUBMITTABLE");
        JsonNode edit = success(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(revision.path("planVersion").asInt(), List.of(Map.of("equipmentId", eq2, "plannedDate", "2026-11-06")))), 200);
        assertThat(edit.path("status").asText()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=?", Integer.class, planId)).isEqualTo(2);
        JsonNode secondSubmit = success(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt,
                Map.of("version", edit.path("version").asInt())), 200);
        long secondRequest = secondSubmit.path("approvalRequestId").asLong();
        assertThat(secondRequest).isNotEqualTo(firstRequest);
        JsonNode approved = success(send(HttpMethod.POST, "/api/approvals/" + secondRequest + "/decision", director,
                Map.of("version", secondSubmit.path("version").asInt(), "outcome", "APPROVE")), 200);
        assertThat(approved.path("planStatus").asText()).isEqualTo("APPROVED");
        assertThat(pendingContains(secondRequest)).isFalse();
        error(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(approved.path("planVersion").asInt(), List.of())), 409, "PLAN_NOT_EDITABLE");
        assertThat(jdbc.queryForList("SELECT status FROM approval_request WHERE plan_id=? ORDER BY id", String.class, planId))
                .containsExactly("DECIDED", "DECIDED");
        assertThat(jdbc.queryForList("SELECT a.outcome FROM approval_action a JOIN approval_request r ON r.id=a.request_id WHERE r.plan_id=? ORDER BY a.id", String.class, planId))
                .containsExactly("REVISION_REQUIRED", "APPROVE");
        assertThat(jdbc.queryForList("SELECT new_state FROM status_history WHERE plan_id=? ORDER BY id", String.class, planId))
                .containsExactly("DRAFT", "SUBMITTED", "REVISION_REQUIRED", "DRAFT", "SUBMITTED", "APPROVED");
        assertThat(jdbc.queryForObject("SELECT reason FROM status_history WHERE plan_id=? AND new_state='REVISION_REQUIRED'", String.class, planId))
                .isEqualTo("Cần đổi lịch");
        assertThat(jdbc.queryForList("SELECT u.role_code FROM status_history h JOIN user_account u ON u.id=h.actor_user_id WHERE h.plan_id=? ORDER BY h.id", String.class, planId))
                .containsExactly("PHONG_VTYT", "PHONG_VTYT", "BAN_GIAM_DOC", "PHONG_VTYT", "PHONG_VTYT", "BAN_GIAM_DOC");
        assertThat(success(send(HttpMethod.GET, "/api/plans/" + planId, vtyt, null), 200).path("status").asText())
                .isEqualTo("APPROVED");
    }

    @Test
    void staleVersionAndDuplicateCommandsLeaveNoPartialEvidence() {
        JsonNode created = create(eq1);
        long planId = created.path("id").asLong();
        int oldVersion = created.path("version").asInt();
        JsonNode edited = success(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(oldVersion, List.of(Map.of("equipmentId", eq2)))), 200);
        error(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt, Map.of("version", oldVersion)),
                409, "OPTIMISTIC_LOCK_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_request WHERE plan_id=?", Integer.class, planId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan WHERE id=?", String.class, planId)).isEqualTo("DRAFT");
        JsonNode submitted = success(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt,
                Map.of("version", edited.path("version").asInt())), 200);
        error(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt,
                Map.of("version", submitted.path("version").asInt())), 409, "PLAN_ALREADY_PENDING_APPROVAL");
        error(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(submitted.path("version").asInt(), List.of())), 409, "PLAN_NOT_EDITABLE");
        long requestId = submitted.path("approvalRequestId").asLong();
        error(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                Map.of("version", oldVersion, "outcome", "APPROVE")), 409, "OPTIMISTIC_LOCK_CONFLICT");
        error(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                Map.of("version", submitted.path("version").asInt(), "outcome", "REVISION_REQUIRED")), 400, "REVISION_COMMENT_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class, requestId)).isZero();
        JsonNode result = success(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE")), 200);
        error(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                Map.of("version", result.path("planVersion").asInt(), "outcome", "APPROVE")), 409,
                "APPROVAL_REQUEST_NOT_PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class, requestId)).isEqualTo(1);
    }

    @Test
    void createValidationAndRollbackAfterFirstItemInsert() {
        String title = "TEST-ROLLBACK-" + UUID.randomUUID();
        error(send(HttpMethod.POST, "/api/plans", vtyt,
                Map.of("title", title, "periodStart", "2026-11-01", "periodEnd", "2026-11-30",
                        "items", List.of(Map.of("equipmentId", eq1), Map.of("equipmentId", eq1)))),
                409, "DUPLICATE_PLAN_EQUIPMENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan WHERE title=?", Integer.class, title)).isZero();
        error(send(HttpMethod.POST, "/api/plans", vtyt,
                Map.of("title", "bad", "periodStart", "2026-11-30", "periodEnd", "2026-11-01",
                        "items", List.of(Map.of("equipmentId", eq1)))), 400, "INVALID_PLAN_PERIOD");
        error(send(HttpMethod.POST, "/api/plans", vtyt,
                Map.of("title", "bad", "periodStart", "2026-11-01", "periodEnd", "2026-11-30",
                        "items", List.of(Map.of("equipmentId", 999999999L)))), 404, "EQUIPMENT_NOT_FOUND");
        jdbc.update("UPDATE equipment SET active=false WHERE id=?", eq3);
        try {
            error(send(HttpMethod.POST, "/api/plans", vtyt,
                    Map.of("title", "inactive", "periodStart", "2026-11-01", "periodEnd", "2026-11-30",
                            "items", List.of(Map.of("equipmentId", eq3)))), 409, "EQUIPMENT_INACTIVE");
        } finally {
            jdbc.update("UPDATE equipment SET active=true WHERE id=?", eq3);
        }
        error(send(HttpMethod.POST, "/api/plans", vtyt,
                Map.of("title", "bad", "periodStart", "2026-11-01", "periodEnd", "2026-11-30",
                        "items", List.of())), 400, "VALIDATION_ERROR");
    }

    @Test
    void planningEditIsAdditiveAndRejectsAuditedRemovalAndBadDates() {
        JsonNode created = create(eq1);
        long planId = created.path("id").asLong();
        int version = created.path("version").asInt();
        Map<String, Object> remove = editBody(version, List.of());
        remove.put("removeEquipmentIds", List.of(eq1));
        error(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt, remove), 409, "PLAN_ITEM_RETENTION_CONFLICT");
        error(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(version, List.of(Map.of("equipmentId", eq3, "plannedDate", "2027-01-01")))),
                400, "INVALID_PLANNED_DATE");
        JsonNode updated = success(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(version, List.of(Map.of("equipmentId", eq3, "plannedDate", "2026-11-15")))), 200);
        assertThat(updated.path("version").asInt()).isGreaterThan(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=?", Integer.class, planId)).isEqualTo(2);
    }

    @Test
    void itemOnlyEditAdvancesPlanVersionAndRecordsEdit() {
        JsonNode created = create(eq1);
        long planId = created.path("id").asLong();
        JsonNode first = success(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(created.path("version").asInt(), List.of())), 200);
        JsonNode second = success(send(HttpMethod.PATCH, "/api/plans/" + planId, vtyt,
                editBody(first.path("version").asInt(),
                        List.of(Map.of("equipmentId", eq1, "plannedDate", "2026-11-16")))), 200);
        assertThat(second.path("version").asInt()).isGreaterThan(first.path("version").asInt());
        assertThat(jdbc.queryForObject("SELECT version FROM maintenance_plan WHERE id=?", Integer.class, planId))
                .isEqualTo(second.path("version").asInt());
        assertThat(jdbc.queryForObject("SELECT planned_date FROM maintenance_plan_item WHERE plan_id=?", java.sql.Date.class, planId)
                .toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 16));
        assertThat(jdbc.queryForList("SELECT action FROM status_history WHERE plan_id=? ORDER BY id", String.class, planId))
                .containsExactly("CREATE", "EDIT_PLAN", "EDIT_PLAN");
        error(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt,
                Map.of("version", first.path("version").asInt())), 409, "OPTIMISTIC_LOCK_CONFLICT");
    }

    @Test
    void directorRejectsRequestWhenTargetPlanIsNoLongerSubmitted() {
        JsonNode created = create(eq1);
        long planId = created.path("id").asLong();
        JsonNode submitted = success(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt,
                Map.of("version", created.path("version").asInt())), 200);
        long requestId = submitted.path("approvalRequestId").asLong();
        jdbc.update("UPDATE maintenance_plan SET status='DRAFT' WHERE id=?", planId);
        try {
            error(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                    Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE")),
                    409, "PLAN_STATE_CONFLICT");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class,
                    requestId)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM approval_request WHERE id=?", String.class,
                    requestId)).isEqualTo("PENDING");
        } finally {
            jdbc.update("UPDATE maintenance_plan SET status='SUBMITTED' WHERE id=?", planId);
        }
    }

    @Test
    void roleBoundariesAndDirectServiceAuthorization() {
        Map<String, Object> body = newPlan(eq1);
        error(send(HttpMethod.POST, "/api/plans", null, body), 401, "AUTHENTICATION_REQUIRED");
        for (String token : List.of(director, department, admin))
            error(send(HttpMethod.POST, "/api/plans", token, body), 403, "ACCESS_DENIED");
        JsonNode created = create(eq1);
        long planId = created.path("id").asLong();
        error(send(HttpMethod.PATCH, "/api/plans/" + planId, department,
                editBody(created.path("version").asInt(), List.of())), 403, "ACCESS_DENIED");
        error(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", admin,
                Map.of("version", created.path("version").asInt())), 403, "ACCESS_DENIED");
        JsonNode submitted = success(send(HttpMethod.POST, "/api/plans/" + planId + "/submit", vtyt,
                Map.of("version", created.path("version").asInt())), 200);
        for (String token : List.of(vtyt, department, admin))
            error(send(HttpMethod.POST, "/api/approvals/" + submitted.path("approvalRequestId").asLong() + "/decision",
                    token, Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE")), 403, "ACCESS_DENIED");
        long accountId = jdbc.queryForObject("SELECT id FROM user_account WHERE username='demo_khoa_noi'", Long.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(accountId, "demo_khoa_noi", UserRole.KHOA_PHONG, 1L), null));
        try {
            assertThatThrownBy(() -> planning.create(new CreatePlanRequest("forbidden",
                    java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 30),
                    List.of(new PlanItemInput(eq1, null)))))
                    .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("BUSINESS_ACCESS_DENIED");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void missingAndWrongRequestTypeAreSafe() {
        error(send(HttpMethod.PATCH, "/api/plans/999999999", vtyt, editBody(0, List.of())), 404, "PLAN_NOT_FOUND");
        error(send(HttpMethod.POST, "/api/plans/999999999/submit", vtyt, Map.of("version", 0)), 404, "PLAN_NOT_FOUND");
        error(send(HttpMethod.POST, "/api/approvals/999999999/decision", director,
                Map.of("version", 0, "outcome", "APPROVE")), 404, "APPROVAL_REQUEST_NOT_FOUND");
        Long vendorId = jdbc.queryForObject("SELECT id FROM approval_request WHERE request_type='VENDOR_SELECTION' AND status='PENDING' ORDER BY id LIMIT 1", Long.class);
        // Shared HTTP endpoint now dispatches vendor requests to UC07; stale item version remains safe.
        error(send(HttpMethod.POST, "/api/approvals/" + vendorId + "/decision", director,
                Map.of("version", 0, "outcome", "APPROVE")), 409, "OPTIMISTIC_LOCK_CONFLICT");
        long directorId = jdbc.queryForObject("SELECT id FROM user_account WHERE username='demo_bgd'", Long.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(directorId, "demo_bgd", UserRole.BAN_GIAM_DOC, null), null));
        try {
            assertThatThrownBy(() -> planApproval.decide(vendorId,
                    new DecidePlanRequest(0, ApprovalOutcome.APPROVE, null)))
                    .isInstanceOf(BusinessRuleException.class).extracting("code")
                    .isEqualTo("APPROVAL_REQUEST_WRONG_TYPE");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean pendingContains(long requestId) {
        JsonNode page = success(send(HttpMethod.GET,
                "/api/approvals/pending?requestType=PLAN_APPROVAL&size=100", director, null), 200);
        for (JsonNode row : page.path("content")) if (row.path("id").asLong() == requestId) return true;
        return false;
    }

    private JsonNode create(long equipmentId) {
        JsonNode body = success(send(HttpMethod.POST, "/api/plans", vtyt, newPlan(equipmentId)), 201);
        createdPlans.add(body.path("id").asLong());
        return body;
    }

    private Map<String, Object> newPlan(long equipmentId) {
        return Map.of("title", "TEST-PLAN-" + UUID.randomUUID(), "periodStart", "2026-11-01",
                "periodEnd", "2026-11-30", "items", List.of(PlanningTestData.complete(jdbc,equipmentId)));
    }

    private Map<String, Object> editBody(int version, List<Map<String, Object>> items) {
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("title", "TEST-UPDATED");
        body.put("periodStart", "2026-11-01");
        body.put("periodEnd", "2026-11-30");
        body.put("items",items.stream().map(row->{var complete=PlanningTestData.complete(jdbc,((Number)row.get("equipmentId")).longValue());complete.putAll(row);return complete;}).toList());
        return body;
    }

    private ResponseEntity<JsonNode> send(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        if (body != null) headers.set("Content-Type", "application/json");
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode success(ResponseEntity<JsonNode> response, int status) {
        assertThat(response.getStatusCode().value()).as(String.valueOf(response.getBody())).isEqualTo(status);
        return response.getBody();
    }

    private void error(ResponseEntity<JsonNode> response, int status, String code) {
        JsonNode body = success(response, status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.toString()).doesNotContain("Exception", "java.lang", "passwordHash", "stackTrace");
    }

    private String login(String name, String role) {
        String password = System.getenv("DEMO_" + role + "_PASSWORD");
        assertThat(password).isNotBlank();
        JsonNode body = success(send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", name, "password", password)), 200);
        return body.path("accessToken").asText();
    }

    private long id(String code) {
        return jdbc.queryForObject("SELECT id FROM equipment WHERE equipment_code=?", Long.class, code);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
