package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
class BackendBusinessFinalIntegrationTest {
    private java.util.Map<Long,String> fixtureStatuses;
    @org.junit.jupiter.api.BeforeEach void isolateOpenFixturePlans() { fixtureStatuses=PlanningTestData.archiveFixturePlans(jdbc); }
    @org.junit.jupiter.api.AfterEach void restoreOpenFixturePlans() { PlanningTestData.restoreFixturePlans(jdbc,fixtureStatuses); }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;
    @MockitoSpyBean WorkflowHistory history;
    private final List<Long> plans = new ArrayList<>();
    private String vtyt, director, department, admin;
    private long freeEq, paidEq, freeCoverage, paidCoverage, provider;

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
    }

    @AfterEach
    void cleanup() {
        reset(history);
        for (long plan : plans) {
            PlanningTestData.cleanNotifications(jdbc,plan);
            jdbc.update("DELETE FROM maintenance_report_delivery WHERE report_id IN (SELECT id FROM maintenance_report WHERE plan_id=?)", plan);
            jdbc.update("DELETE FROM maintenance_report WHERE plan_id=?", plan);
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
        assertThat(count("maintenance_report")).isEqualTo(4);
        assertThat(count("maintenance_execution")).isEqualTo(30);
        assertThat(count("status_history")).isEqualTo(240);
    }

    @Test
    void fullHttpWorkflowCreatesFinalReportAndReadableHistory() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        long execution = start(plan, item);
        ok(send(HttpMethod.POST, "/api/executions/" + execution + "/progress", vtyt,
                Map.of("workNote", "Đã bảo trì thiết bị"), null), 201);
        finish(execution, item);
        technical(execution, item, "PASS", false);
        handover(execution, item, "PASS", false);
        assertThat(planStatus(plan)).isEqualTo("AWAITING_REPORT");
        JsonNode evidence = ok(send(HttpMethod.GET, "/api/plans/" + plan + "/report/evidence", vtyt, null, null), 200);
        assertThat(evidence.path("planId").asLong()).isEqualTo(plan);
        assertThat(evidence.path("items").size()).isEqualTo(1);
        JsonNode aggregated = evidence.path("items").get(0);
        assertThat(aggregated.path("equipmentCode").asText()).isEqualTo("DEMO-EQ-001");
        assertThat(aggregated.path("providerName").asText()).isNotBlank();
        assertThat(aggregated.path("attempts").get(0).path("progress").get(0).path("workNote").asText()).isEqualTo("Đã bảo trì thiết bị");
        assertThat(aggregated.path("attempts").get(0).path("technicalAcceptance").path("result").asText()).isEqualTo("PASS");
        assertThat(aggregated.path("attempts").get(0).path("handoverAcceptance").path("result").asText()).isEqualTo("PASS");
        assertSafe(evidence);
        JsonNode draft = createReport(plan, "Đã hoàn thành bảo trì theo kế hoạch");
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("planStatus").asText()).isEqualTo("AWAITING_REPORT");
        assertThat(draft.path("completedCount").asLong()).isEqualTo(1);
        assertThat(draft.path("repairRequiredCount").asLong()).isZero();
        JsonNode finalReport = finalizeReport(plan, draft.path("planVersion").asInt());
        assertThat(finalReport.path("status").asText()).isEqualTo("FINAL");
        assertThat(finalReport.path("planStatus").asText()).isEqualTo("REPORTED");
        assertThat(finalReport.path("finalizedAt").asText()).isNotBlank();
        assertThat(planStates(plan)).containsExactly("DRAFT", "SUBMITTED", "APPROVED",
                "IN_PROGRESS", "AWAITING_REPORT", "REPORTED");
        JsonNode historyResponse = ok(send(HttpMethod.GET,
                "/api/equipment/" + freeEq + "/maintenance-history", vtyt, null, null), 200);
        JsonNode campaign = campaign(historyResponse, plan);
        assertThat(campaign.path("itemStatus").asText()).isEqualTo("COMPLETED");
        assertThat(campaign.path("attempts").size()).isEqualTo(1);
        assertThat(campaign.path("attempts").get(0).path("progress").size()).isEqualTo(1);
        assertThat(campaign.path("attempts").get(0).path("technicalAcceptance").path("result").asText()).isEqualTo("PASS");
        assertThat(campaign.path("attempts").get(0).path("handoverAcceptance").path("result").asText()).isEqualTo("PASS");
        assertThat(campaign.path("report").path("status").asText()).isEqualTo("FINAL");
        ok(send(HttpMethod.POST,"/api/plans/"+plan+"/report/send",vtyt,Map.of("version",planVersion(plan)),null),200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_notification WHERE notification_type='REPORT_SHARED' AND target_url=?",Integer.class,"/plans/"+plan+"/report")).isGreaterThan(0);
        assertThat(campaign.path("planHistory").size()).isEqualTo(6);
        assertSafe(historyResponse);
    }

    @Test
    void multiOutcomeReportKeepsCompletedAndRepairSeparate() {
        long plan = approvedPlan(freeEq, paidEq);
        long completedItem = routeFree(plan, freeEq);
        long repairItem = routeExternal(plan, paidEq);
        long doneExecution = start(plan, completedItem);
        finish(doneExecution, completedItem);
        technical(doneExecution, completedItem, "PASS", false);
        handover(doneExecution, completedItem, "PASS", false);
        assertThat(planStatus(plan)).isEqualTo("IN_PROGRESS");
        long repairExecution = start(plan, repairItem);
        ok(send(HttpMethod.POST, "/api/executions/" + repairExecution + "/repair-required", vtyt,
                Map.of("version", itemVersion(repairItem), "reason", "Hư hỏng cần chuyển sửa chữa"), null), 200);
        assertThat(planStatus(plan)).isEqualTo("AWAITING_REPORT");
        JsonNode draft = createReport(plan, "Một thiết bị hoàn tất; một thiết bị chuyển sửa chữa");
        assertThat(draft.path("completedCount").asLong()).isEqualTo(1);
        assertThat(draft.path("repairRequiredCount").asLong()).isEqualTo(1);
        finalizeReport(plan, draft.path("planVersion").asInt());
        JsonNode repairCampaign = campaign(ok(send(HttpMethod.GET,
                "/api/equipment/" + paidEq + "/maintenance-history", vtyt, null, null), 200), plan);
        assertThat(repairCampaign.path("itemStatus").asText()).isEqualTo("REPAIR_REQUIRED");
        assertThat(repairCampaign.path("attempts").get(0).path("progress").get(0).path("damageNote").asText()).isNotBlank();
        assertThat(repairCampaign.path("itemHistory").toString()).contains("REPAIR_REQUIRED");
    }

    @Test
    void draftEditUniquenessStaleVersionAndFinalImmutability() {
        long plan = reportablePlan();
        int before = planVersion(plan);
        fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report", vtyt,
                Map.of("version", before-1, "workDone", "old"), null), 409, "OPTIMISTIC_LOCK_CONFLICT");
        JsonNode draft = createReport(plan, "");
        assertThat(draft.path("planVersion").asInt()).isGreaterThan(before);
        fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report", vtyt,
                Map.of("version", draft.path("planVersion").asInt(), "workDone", "duplicate"), null),
                409, "REPORT_ALREADY_EXISTS");
        fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report/finalize", vtyt,
                Map.of("version", draft.path("planVersion").asInt()), null),
                400, "REPORT_WORK_DONE_REQUIRED");
        JsonNode edited = ok(send(HttpMethod.PUT, "/api/plans/" + plan + "/report", vtyt,
                Map.of("version", draft.path("planVersion").asInt(), "workDone", "Nội dung đã bổ sung"), null), 200);
        assertThat(edited.path("workDone").asText()).isEqualTo("Nội dung đã bổ sung");
        fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report/finalize", vtyt,
                Map.of("version", draft.path("planVersion").asInt()), null),
                409, "OPTIMISTIC_LOCK_CONFLICT");
        JsonNode finalReport = finalizeReport(plan, edited.path("planVersion").asInt());
        fail(send(HttpMethod.PUT, "/api/plans/" + plan + "/report", vtyt,
                Map.of("version", finalReport.path("planVersion").asInt(), "workDone", "overwrite"), null),
                409, "REPORT_NOT_EDITABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_report WHERE plan_id=?", Integer.class, plan)).isEqualTo(1);
    }

    @Test
    void reportWrongStateAndNonterminalItemAreRejected() {
        long plan = approvedPlan(freeEq);
        fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report", vtyt,
                Map.of("version", planVersion(plan), "workDone", "early"), null),
                409, "PLAN_NOT_REPORTABLE");
        long item = routeFree(plan, freeEq);
        start(plan, item);
        fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report", vtyt,
                Map.of("version", planVersion(plan), "workDone", "still working"), null),
                409, "PLAN_NOT_REPORTABLE");
        jdbc.update("UPDATE maintenance_plan SET status='AWAITING_REPORT' WHERE id=?", plan);
        try {
            fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report", vtyt,
                    Map.of("version", planVersion(plan), "workDone", "false"), null),
                    409, "PLAN_NOT_REPORTABLE");
        } finally {
            jdbc.update("UPDATE maintenance_plan SET status='IN_PROGRESS' WHERE id=?", plan);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM maintenance_report WHERE plan_id=?", Integer.class, plan)).isZero();
    }

    @Test
    void lateHistoryFailureRollsBackFinalization() {
        long plan = reportablePlan();
        JsonNode draft = createReport(plan, "Hoàn thành bảo trì");
        doThrow(new IllegalStateException("test-only history failure")).when(history).plan(
                any(), any(), eq("AWAITING_REPORT"), eq("REPORTED"), eq("FINALIZE_REPORT"),
                isNull(), any());
        try {
            fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report/finalize", vtyt,
                    Map.of("version", draft.path("planVersion").asInt()), null),
                    500, "INTERNAL_ERROR");
        } finally { reset(history); }
        assertThat(planStatus(plan)).isEqualTo("AWAITING_REPORT");
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_report WHERE plan_id=?", String.class, plan)).isEqualTo("DRAFT");
        assertThat(planStates(plan)).doesNotContain("REPORTED");
    }

    @Test
    void reportAndHistoryRoleScopeAndNotFound() {
        long plan = reportablePlan();
        JsonNode draft = createReport(plan, "Hoàn thành");
        finalizeReport(plan, draft.path("planVersion").asInt());
        ok(send(HttpMethod.GET, "/api/plans/" + plan + "/report", director, null, null), 200);
        ok(send(HttpMethod.GET, "/api/plans/" + plan + "/report/evidence", director, null, null), 200);
        for (String token : List.of(department, admin))
            fail(send(HttpMethod.GET, "/api/plans/" + plan + "/report/evidence", token, null, null), 403, "ACCESS_DENIED");
        fail(send(HttpMethod.PUT, "/api/plans/" + plan + "/report", director,
                Map.of("version", planVersion(plan), "workDone", "overwrite"), null), 403, "ACCESS_DENIED");
        fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report/finalize", director,
                Map.of("version", planVersion(plan)), null), 403, "ACCESS_DENIED");

        for (String token : List.of(department, admin))
            fail(send(HttpMethod.GET, "/api/plans/" + plan + "/report", token, null, null), 403, "ACCESS_DENIED");
        for (String token : List.of(director, department, admin))
            fail(send(HttpMethod.POST, "/api/plans/" + plan + "/report", token,
                    Map.of("version", planVersion(plan)), null), 403, "ACCESS_DENIED");
        ok(send(HttpMethod.GET, "/api/equipment/" + freeEq + "/maintenance-history", department, null, null), 200);
        fail(send(HttpMethod.GET, "/api/equipment/" + freeEq + "/maintenance-history", admin, null, null),
                403, "ACCESS_DENIED");
        fail(send(HttpMethod.GET, "/api/equipment/999999999/maintenance-history", vtyt, null, null),
                404, "EQUIPMENT_NOT_FOUND");
        long other = jdbc.queryForObject("""
                SELECT e.id FROM equipment e JOIN department d ON d.id=e.department_id
                WHERE d.code<>'KHOA_NOI'
                AND NOT EXISTS (
                    SELECT 1 FROM maintenance_plan_item i JOIN department x ON x.id=i.department_id_at_plan
                    WHERE i.equipment_id=e.id AND x.code='KHOA_NOI')
                ORDER BY e.id LIMIT 1
                """, Long.class);
        fail(send(HttpMethod.GET, "/api/equipment/" + other + "/maintenance-history", department, null, null),
                403, "EQUIPMENT_HISTORY_ACCESS_DENIED");
    }

    @Test
    void seededEquipmentHistoryHasMultipleCampaignsAndFailedAttempts() {
        long multi = equipment("DEMO-EQ-004");
        JsonNode response = ok(send(HttpMethod.GET, "/api/equipment/" + multi + "/maintenance-history",
                vtyt, null, null), 200);
        assertThat(response.path("campaigns").size()).isGreaterThanOrEqualTo(2);
        assertThat(response.path("campaigns").toString()).contains("COMPLETED", "REPAIR_REQUIRED");
        long attemptItem = jdbc.queryForObject("""
                SELECT plan_item_id FROM maintenance_execution
                GROUP BY plan_item_id HAVING count(*) > 1 ORDER BY plan_item_id LIMIT 1
                """, Long.class);
        long attemptEquipment = jdbc.queryForObject("SELECT equipment_id FROM maintenance_plan_item WHERE id=?",
                Long.class, attemptItem);
        JsonNode multiAttempt = ok(send(HttpMethod.GET,
                "/api/equipment/" + attemptEquipment + "/maintenance-history", vtyt, null, null), 200);
        JsonNode matching = null;
        for (JsonNode row : multiAttempt.path("campaigns"))
            if (row.path("itemId").asLong() == attemptItem) matching = row;
        assertThat(matching).isNotNull();
        assertThat(matching.path("attempts").size()).isEqualTo(2);
        assertThat(matching.path("attempts").get(0).path("attemptNo").asInt()).isEqualTo(1);
        assertThat(matching.path("attempts").get(1).path("attemptNo").asInt()).isEqualTo(2);
        assertThat(matching.path("attempts").toString()).contains("FAIL");
        assertSafe(multiAttempt);
    }

    @Test
    void failedTechnicalAttemptRemainsVisibleAfterSuccessfulReworkAndReport() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        long first = start(plan, item);
        finish(first, item);
        technical(first, item, "FAIL", false);
        long second = start(plan, item);
        finish(second, item);
        technical(second, item, "PASS", false);
        handover(second, item, "PASS", false);
        JsonNode draft = createReport(plan, "Sau xử lý lại đã hoàn tất");
        finalizeReport(plan, draft.path("planVersion").asInt());
        JsonNode campaign = campaign(ok(send(HttpMethod.GET,
                "/api/equipment/" + freeEq + "/maintenance-history", vtyt, null, null), 200), plan);
        assertThat(campaign.path("attempts").size()).isEqualTo(2);
        assertThat(campaign.path("attempts").get(0).path("technicalAcceptance").path("result").asText()).isEqualTo("FAIL");
        assertThat(campaign.path("attempts").get(1).path("technicalAcceptance").path("result").asText()).isEqualTo("PASS");
        assertThat(campaign.path("report").path("status").asText()).isEqualTo("FINAL");
    }

    @Test
    void historicalDepartmentCampaignRemainsScopedAfterEquipmentMove() {
        long plan = reportablePlan();
        JsonNode draft = createReport(plan, "Báo cáo");
        finalizeReport(plan, draft.path("planVersion").asInt());
        long original = jdbc.queryForObject("SELECT department_id FROM equipment WHERE id=?", Long.class, freeEq);
        long other = jdbc.queryForObject("SELECT id FROM department WHERE id<>? ORDER BY id LIMIT 1", Long.class, original);
        jdbc.update("UPDATE equipment SET department_id=? WHERE id=?", other, freeEq);
        try {
            JsonNode response = ok(send(HttpMethod.GET,
                    "/api/equipment/" + freeEq + "/maintenance-history", department, null, null), 200);
            assertThat(campaign(response, plan).path("itemStatus").asText()).isEqualTo("COMPLETED");
            assertThat(response.path("currentDepartmentId").isNull()).isTrue();
            assertThat(campaign(response, plan).path("coverageId").isNull()).isTrue();
            assertThat(campaign(response, plan).path("planHistory").size()).isZero();
        } finally {
            jdbc.update("UPDATE equipment SET department_id=? WHERE id=?", original, freeEq);
        }
    }

    @Test
    void existingEquipmentAndPlanReadsRespectDepartmentScope() {
        long other = jdbc.queryForObject("""
                SELECT e.id FROM equipment e JOIN department d ON d.id=e.department_id
                WHERE d.code<>'KHOA_NOI'
                AND NOT EXISTS (
                    SELECT 1 FROM maintenance_plan_item i JOIN department x ON x.id=i.department_id_at_plan
                    WHERE i.equipment_id=e.id AND x.code='KHOA_NOI')
                ORDER BY e.id LIMIT 1
                """, Long.class);
        JsonNode equipmentPage = ok(send(HttpMethod.GET, "/api/equipment?size=100",
                department, null, null), 200);
        for (JsonNode row : equipmentPage.path("content"))
            assertThat(row.path("id").asLong()).isNotEqualTo(other);
        JsonNode broadSearch = ok(send(HttpMethod.GET, "/api/equipment?size=100&search=demo-eq",
                director, null, null), 200);
        assertThat(broadSearch.path("content").size()).isEqualTo(count("equipment"));
        JsonNode scopedSearch = ok(send(HttpMethod.GET, "/api/equipment?size=100&search=DEMO-EQ",
                department, null, null), 200);
        assertThat(scopedSearch.path("totalElements")).isEqualTo(equipmentPage.path("totalElements"));
        for (JsonNode row : scopedSearch.path("content"))
            assertThat(row.path("id").asLong()).isNotEqualTo(other);
        assertThat(ok(send(HttpMethod.GET, "/api/equipment?search=%25", director, null, null), 200)
                .path("totalElements").asInt()).isZero();
        String serial = jdbc.queryForObject("SELECT serial_number FROM equipment WHERE id=?", String.class, freeEq);
        assertThat(ok(send(HttpMethod.GET, "/api/equipment?search=" + serial, director, null, null), 200)
                .path("content").get(0).path("id").asLong()).isEqualTo(freeEq);

        fail(send(HttpMethod.GET, "/api/equipment/" + other, department, null, null),
                403, "DEPARTMENT_SCOPE_VIOLATION");
        long otherDepartment = jdbc.queryForObject("SELECT department_id FROM equipment WHERE id=?",
                Long.class, other);
        fail(send(HttpMethod.GET, "/api/equipment?departmentId=" + otherDepartment,
                department, null, null), 403, "DEPARTMENT_SCOPE_VIOLATION");

        long plan = approvedPlan(freeEq, other);
        JsonNode planPage = ok(send(HttpMethod.GET, "/api/plans?size=100", department, null, null), 200);
        assertThat(planPage.path("content").toString()).contains(String.valueOf(plan));
        JsonNode scopedItems = ok(send(HttpMethod.GET, "/api/plans/" + plan + "/items?size=100",
                department, null, null), 200);
        assertThat(scopedItems.path("content").size()).isEqualTo(1);
        assertThat(scopedItems.path("content").get(0).path("equipmentId").asLong()).isEqualTo(freeEq);
        for (JsonNode row : scopedItems.path("content"))
            assertThat(row.path("equipmentId").asLong()).isNotEqualTo(other);
        jdbc.update("UPDATE maintenance_plan_item SET status='AWAITING_HANDOVER' WHERE plan_id=? AND equipment_id=?", plan, other);
        JsonNode queue = ok(send(HttpMethod.GET, "/api/plans?size=100&itemStatus=AWAITING_HANDOVER", department, null, null), 200);
        for (JsonNode row : queue.path("content")) assertThat(row.path("id").asLong()).isNotEqualTo(plan);
        jdbc.update("UPDATE maintenance_plan_item SET status='AWAITING_HANDOVER' WHERE plan_id=? AND equipment_id=?", plan, freeEq);
        JsonNode waiting = ok(send(HttpMethod.GET, "/api/plans/" + plan + "/items?status=AWAITING_HANDOVER&size=100", department, null, null), 200);
        assertThat(waiting.path("content").size()).isEqualTo(1);
        assertThat(waiting.path("content").get(0).path("equipmentId").asLong()).isEqualTo(freeEq);
        queue = ok(send(HttpMethod.GET, "/api/plans?size=100&itemStatus=AWAITING_HANDOVER", department, null, null), 200);
        boolean found = false;
        for (JsonNode row : queue.path("content")) if (row.path("id").asLong() == plan) found = true;
        assertThat(found).isTrue();

        long foreignPlan = jdbc.queryForObject("""
                SELECT p.id FROM maintenance_plan p
                WHERE NOT EXISTS (
                    SELECT 1 FROM maintenance_plan_item i JOIN department d ON d.id=i.department_id_at_plan
                    WHERE i.plan_id=p.id AND d.code='KHOA_NOI')
                ORDER BY p.id LIMIT 1
                """, Long.class);
        fail(send(HttpMethod.GET, "/api/plans/" + foreignPlan, department, null, null),
                403, "DEPARTMENT_SCOPE_VIOLATION");
    }

    @Test
    void historyHttpQueryCountStaysBoundedOnDemoCampaigns() {
        long single = equipment("DEMO-EQ-001");
        long multi = equipment("DEMO-EQ-004");
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            ok(send(HttpMethod.GET, "/api/equipment/" + single + "/maintenance-history", vtyt, null, null), 200);
            long singleSql = stats.getPrepareStatementCount();
            stats.clear();
            ok(send(HttpMethod.GET, "/api/equipment/" + multi + "/maintenance-history", vtyt, null, null), 200);
            long multiSql = stats.getPrepareStatementCount();
            long attemptItem = jdbc.queryForObject("""
                    SELECT plan_item_id FROM maintenance_execution
                    GROUP BY plan_item_id HAVING count(*) > 1 ORDER BY plan_item_id LIMIT 1
                    """, Long.class);
            long reworkEquipment = jdbc.queryForObject(
                    "SELECT equipment_id FROM maintenance_plan_item WHERE id=?", Long.class, attemptItem);
            stats.clear();
            ok(send(HttpMethod.GET, "/api/equipment/" + reworkEquipment + "/maintenance-history",
                    vtyt, null, null), 200);
            long reworkSql = stats.getPrepareStatementCount();
            assertThat(singleSql).isBetween(3L, 12L);
            assertThat(multiSql).isBetween(3L, 12L);
            assertThat(reworkSql).isBetween(3L, 12L);
            assertThat(multiSql).isLessThanOrEqualTo(singleSql + 2L);
            System.out.println("UC12 HTTP SQL: single=" + singleSql + ", multi=" + multiSql
                    + ", rework=" + reworkSql);
        } finally { stats.setStatisticsEnabled(false); }
    }

    private long reportablePlan() {
        long plan = approvedPlan(freeEq);
        long item = routeFree(plan, freeEq);
        long execution = start(plan, item);
        finish(execution, item);
        technical(execution, item, "PASS", false);
        handover(execution, item, "PASS", false);
        return plan;
    }

    private long approvedPlan(long... equipmentIds) {
        List<Map<String, Object>> selections = new ArrayList<>();
        for (long id : equipmentIds) selections.add(PlanningTestData.complete(jdbc,id));
        JsonNode created = ok(send(HttpMethod.POST, "/api/plans", vtyt,
                Map.of("title", "TEST-REPORT-" + UUID.randomUUID(), "periodStart", "2026-11-01",
                        "periodEnd", "2026-11-30", "items", selections), null), 201);
        long plan = created.path("id").asLong();
        plans.add(plan);
        JsonNode submitted = ok(send(HttpMethod.POST, "/api/plans/" + plan + "/submit", vtyt,
                Map.of("version", created.path("version").asInt()), null), 200);
        ok(send(HttpMethod.POST, "/api/approvals/" + submitted.path("approvalRequestId").asLong() + "/decision",
                director, Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE"), null), 200);
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

    private long start(long plan, long item) {
        return ok(send(HttpMethod.POST, "/api/plan-items/" + item + "/executions", vtyt,
                Map.of("version", itemVersion(item), "planVersion", planVersion(plan)), null), 201)
                .path("executionId").asLong();
    }

    private void finish(long execution, long item) {
        ok(send(HttpMethod.POST, "/api/executions/" + execution + "/complete-work", vtyt,
                Map.of("version", itemVersion(item), "resultNote", "Đã hoàn tất công việc"), null), 200);
    }

    private void technical(long execution, long item, String result, boolean repair) {
        ok(send(HttpMethod.POST, "/api/executions/" + execution + "/technical-acceptance", vtyt,
                Map.of("version", itemVersion(item), "result", result,
                        "conclusion", result.equals("PASS") ? "Đạt" : "Cần xử lý lại",
                        "repairRequired", repair), null), 201);
    }

    private void handover(long execution, long item, String result, boolean repair) {
        ok(send(HttpMethod.POST, "/api/executions/" + execution + "/handover", department,
                Map.of("version", itemVersion(item), "result", result,
                        "conclusion", result.equals("PASS") ? "Đạt" : "Cần xử lý lại",
                        "repairRequired", repair), vtyt), 201);
    }

    private JsonNode createReport(long plan, String workDone) {
        return ok(send(HttpMethod.POST, "/api/plans/" + plan + "/report", vtyt,
                Map.of("version", planVersion(plan), "workDone", workDone), null), 201);
    }

    private JsonNode finalizeReport(long plan, int version) {
        return ok(send(HttpMethod.POST, "/api/plans/" + plan + "/report/finalize", vtyt,
                Map.of("version", version), null), 200);
    }

    private JsonNode campaign(JsonNode response, long plan) {
        for (JsonNode row : response.path("campaigns"))
            if (row.path("planId").asLong() == plan) return row;
        throw new AssertionError("Missing campaign " + plan);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
    private long equipment(String code) {
        return jdbc.queryForObject("SELECT id FROM equipment WHERE equipment_code=?", Long.class, code);
    }
    private long coverage(long equipment) {
        return jdbc.queryForObject("SELECT id FROM maintenance_coverage WHERE equipment_id=? ORDER BY id LIMIT 1",
                Long.class, equipment);
    }
    private long item(long plan, long equipment) {
        return jdbc.queryForObject("SELECT id FROM maintenance_plan_item WHERE plan_id=? AND equipment_id=?",
                Long.class, plan, equipment);
    }
    private int itemVersion(long item) {
        return jdbc.queryForObject("SELECT version FROM maintenance_plan_item WHERE id=?", Integer.class, item);
    }
    private int planVersion(long plan) {
        return jdbc.queryForObject("SELECT version FROM maintenance_plan WHERE id=?", Integer.class, plan);
    }
    private String planStatus(long plan) {
        return jdbc.queryForObject("SELECT status FROM maintenance_plan WHERE id=?", String.class, plan);
    }
    private List<String> planStates(long plan) {
        return jdbc.queryForList("SELECT new_state FROM status_history WHERE plan_id=? ORDER BY id", String.class, plan);
    }
    private String login(String name, String role) {
        String password = System.getenv("DEMO_" + role + "_PASSWORD");
        assertThat(password).isNotBlank();
        return ok(send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", name, "password", password), null), 200).path("accessToken").asText();
    }
    private ResponseEntity<JsonNode> send(HttpMethod method, String path, String token, Object body, String cosigner) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        if (cosigner != null) headers.set("X-VTYT-Authorization", "Bearer " + cosigner);
        if (body != null) headers.set("Content-Type", "application/json");
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode ok(ResponseEntity<JsonNode> response, int status) {
        assertThat(response.getStatusCode().value()).as(String.valueOf(response.getBody())).isEqualTo(status);
        return response.getBody();
    }
    private void fail(ResponseEntity<JsonNode> response, int status, String code) {
        JsonNode body = ok(response, status);
        assertThat(body.path("code").asText()).isEqualTo(code);
        assertSafe(body);
    }
    private void assertSafe(JsonNode body) {
        assertThat(body.toString()).doesNotContain("passwordHash", "hibernateLazyInitializer",
                "stackTrace", "jwt", "technicalSpec", "java.lang");
    }
}
