package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.medmaintenance.persistence.enums.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @TestInstance(TestInstance.Lifecycle.PER_CLASS) class ProviderRoutingIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean vn.edu.medmaintenance.persistence.repository.UserNotificationRepository notifications;
    private String vtyt, bgd, khoa, admin;
    private long eq, paid, missing, coverage, provider, provider2;
    private final List<Long> plans=new ArrayList<>();
    private final List<Long> projectionEquipment=new ArrayList<>();
    @BeforeAll void setup() {
        vtyt=login("demo_vtyt", "VTYT");
        bgd=login("demo_bgd", "BGD");
        khoa=login("demo_khoa_noi", "KHOA");
        admin=login("demo_admin", "ADMIN");
        eq=id("DEMO-EQ-001");
        paid=id("DEMO-EQ-002");
        missing=id("DEMO-EQ-036");
        coverage=number("SELECT id FROM maintenance_coverage WHERE equipment_id=?", eq);
        provider=number("SELECT id FROM service_provider WHERE active=true ORDER BY id LIMIT 1");
        provider2=number("SELECT id FROM service_provider WHERE active=true AND id<>? ORDER BY id LIMIT 1", provider);
    }
    @AfterEach void clean() {
        reset(notifications);
        for (var plan:plans) {
            PlanningTestData.cleanNotifications(jdbc, plan);
            jdbc.update("DELETE FROM acceptance_record WHERE execution_id IN (SELECT e.id FROM maintenance_execution e JOIN maintenance_plan_item i ON i.id=e.plan_item_id WHERE i.plan_id=?)", plan);
            jdbc.update("DELETE FROM maintenance_execution WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", plan);
            jdbc.update("DELETE FROM approval_action WHERE request_id IN (SELECT id FROM approval_request WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?))", plan, plan);
            jdbc.update("DELETE FROM approval_request WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", plan, plan);
            jdbc.update("DELETE FROM status_history WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", plan, plan);
            jdbc.update("DELETE FROM maintenance_plan_item WHERE plan_id=?", plan);
            jdbc.update("DELETE FROM maintenance_plan WHERE id=?", plan);
        }
        plans.clear();
        for (long device:projectionEquipment)jdbc.update("DELETE FROM equipment WHERE id=? AND equipment_code LIKE 'SMOKE-V2-PROJECTION-%'", device);
        projectionEquipment.clear();
    }
    @Test void freeDecisionDerivesContractualProviderAndHistory() {
        var p=create(free());
        long i=item(p);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("UNDER_CONTRACT");
        assertThat(number("SELECT assigned_provider_id FROM maintenance_plan_item WHERE id=?", i)).isEqualTo(number("SELECT provider_id FROM maintenance_coverage WHERE id=?", coverage));
        assertThat(text("SELECT reason FROM status_history WHERE plan_item_id=? AND action='SELECT_CONTRACT_COVERAGE'", i)).contains("coverage=", "date=", "basis=");
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id=?", i)).isZero();
    }
    @Test void externalPreparationDoesNotAssignOrCreatePending() {
        var p=create(external(paid));
        long i=item(p);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("PENDING_PROPOSAL");
        assertThat(number("SELECT count(*) FROM maintenance_plan_item WHERE id=? AND assigned_provider_id IS NULL AND assignment_route IS NULL", i)).isEqualTo(1);
        assertThat(text("SELECT status FROM approval_request WHERE plan_item_id=?", i)).isEqualTo("DRAFT");
        assertThat(number("SELECT count(*) FROM user_notification WHERE notification_type='VENDOR_PENDING' AND target_url IN (SELECT '/approvals/'||id FROM approval_request WHERE plan_item_id=?)", i)).isZero();
    }
    @Test void missingCoverageAllowsExplicitExternalProposal() {
        var p=create(external(missing));
        assertThat(submit(p).path("status").asText()).isEqualTo("SUBMITTED");
    }
    @Test void migratedUnverifiedCoverageAllowsExternal() {
        var p=create(external(id("DEMO-EQ-003")));
        assertThat(submit(p).path("status").asText()).isEqualTo("SUBMITTED");
    }
    @Test void incompleteClassificationBlocksSubmissionWithEquipmentCode() {
        var p=create(Map.of("equipmentId", eq));
        var fail=send(HttpMethod.POST, "/api/plans/"+p+"/submit", vtyt, Map.of("version", pv(p)));
        assertThat(ok(fail, 409).path("message").asText()).contains("DEMO-EQ-001");
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("DRAFT");
    }
    @Test void externalProviderRequiredAtSubmission() {
        var input=new HashMap<>(external(paid));
        input.remove("proposedProviderId");
        var p=create(input);
        assertCode(send(HttpMethod.POST, "/api/plans/"+p+"/submit", vtyt, Map.of("version", pv(p))), 409, "PLAN_ITEM_INCOMPLETE");
    }
    @Test void externalBasisRequiredAtSubmission() {
        var input=new HashMap<>(external(paid));
        input.put("rationale", "  ");
        var p=create(input);
        assertCode(send(HttpMethod.POST, "/api/plans/"+p+"/submit", vtyt, Map.of("version", pv(p))), 409, "PLAN_ITEM_INCOMPLETE");
    }
    @Test void inactiveProviderRejectedAtomically() {
        jdbc.update("UPDATE service_provider SET active=false WHERE id=?", provider);
        try {
            assertCode(send(HttpMethod.POST, "/api/plans", vtyt, body(external(paid))), 409, "PROVIDER_INACTIVE");
        }
        finally {
            jdbc.update("UPDATE service_provider SET active=true WHERE id=?", provider);
        }
        assertThat(number("SELECT count(*) FROM maintenance_plan WHERE title='TEST-V2-FAILED'")).isZero();
    }
    @Test void providerDeactivatedAfterPreparationBlocksSubmit() {
        var p=create(external(paid));
        jdbc.update("UPDATE service_provider SET active=false WHERE id=?", provider);
        try {
            assertCode(send(HttpMethod.POST, "/api/plans/"+p+"/submit", vtyt, Map.of("version", pv(p))), 409, "PLAN_ITEM_INCOMPLETE");
        }
        finally {
            jdbc.update("UPDATE service_provider SET active=true WHERE id=?", provider);
        }
    }
    @ParameterizedTest @ValueSource(strings= {
        "SUBMITTED", "APPROVED", "IN_PROGRESS", "AWAITING_REPORT", "REPORTED", "CLOSED"
    }) void lockedPlanRejectsAllFieldMutation(String status) {
        var p=create(free());
        jdbc.update("UPDATE maintenance_plan SET status=? WHERE id=?", status, p);
        assertCode(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, external(eq))), 409, "PLAN_NOT_EDITABLE");
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?", p)).isEqualTo("UNDER_CONTRACT");
    }
    @ParameterizedTest @ValueSource(strings= {
        "DRAFT", "REVISION_REQUIRED"
    }) void editablePlanCanChangeDateMethodAndProposal(String status) {
        var p=create(free());
        jdbc.update("UPDATE maintenance_plan SET status=? WHERE id=?", status, p);
        var input=new HashMap<>(external(eq));
        input.put("plannedDate", "2026-11-16");
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, input)), 200);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?", p)).isEqualTo("PENDING_PROPOSAL");
        assertThat(submit(p).path("status").asText()).isEqualTo("SUBMITTED");
    }
    @Test void revisionCanChangeBackToFreePreservingOldContent() {
        var p=create(external(eq));
        long i=item(p);
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, free())), 200);
        assertThat(text("SELECT status FROM approval_request WHERE plan_item_id=?", i)).isEqualTo("CANCELLED");
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("UNDER_CONTRACT");
    }
    @Test void proposalReplacementPreservesOldBasisAndHasOneActive() {
        var p=create(external(paid));
        long i=item(p);
        var input=new HashMap<>(external(paid));
        input.put("proposedProviderId", provider2);
        input.put("rationale", "Đơn vị thay thế");
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, input)), 200);
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id=? AND status='DRAFT'", i)).isEqualTo(1);
        assertThat(text("SELECT rationale FROM approval_request WHERE plan_item_id=? AND status='CANCELLED'", i)).isEqualTo("Năng lực phù hợp");
    }
    @Test void completePlanSubmitNotifiesOnlyActiveDirectors() {
        var p=create(free());
        var s=submit(p);
        var request=s.path("approvalRequestId").asLong();
        assertThat(number("SELECT count(*) FROM user_notification n JOIN user_account u ON u.id=n.user_account_id WHERE n.target_url=? AND u.role_code='BAN_GIAM_DOC' AND u.active=true", "/approvals/"+request)).isEqualTo(number("SELECT count(*) FROM user_account WHERE role_code='BAN_GIAM_DOC' AND active=true"));
        assertThat(number("SELECT count(*) FROM user_notification n JOIN user_account u ON u.id=n.user_account_id WHERE n.target_url=? AND (u.role_code='ADMIN' OR u.active=false)", "/approvals/"+request)).isZero();
    }
    @Test void freePlanApprovalHasNoVendorRequestAndNotifiesVtyt() {
        var p=create(free());
        approve(p);
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isZero();
        assertThat(number("SELECT count(*) FROM user_notification WHERE notification_type='PLAN_APPROVED' AND target_url=?", "/plans/"+p)).isGreaterThan(0);
    }
    @Test void planApprovalAutomaticallyActivatesPreparedVendorWithoutDuplicates() {
        var p=create(external(paid));
        long i=item(p);
        var a=approve(p);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("WAITING_VENDOR_APPROVAL");
        var q=number("SELECT id FROM approval_request WHERE plan_item_id=? AND status='PENDING'", i);
        assertThat(text("SELECT rationale FROM approval_request WHERE id=?", q)).isEqualTo("Năng lực phù hợp");
        assertThat(number("SELECT count(*) FROM user_notification WHERE notification_type='VENDOR_PENDING' AND target_url=?", "/approvals/"+q)).isGreaterThan(0);
        assertCode(send(HttpMethod.POST, "/api/approvals/"+a.path("requestId").asLong()+"/decision", bgd, Map.of("version", pv(p), "outcome", "APPROVE")), 409, "APPROVAL_REQUEST_NOT_PENDING");
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id=?", i)).isEqualTo(1);
    }
    @Test void vendorApproveAssignsProposedProviderAndNotifies() {
        var p=create(external(paid));
        approve(p);
        long i=item(p);
        long q=pending(i);
        var a=decide(q, iv(i), "APPROVE");
        assertThat(a.path("status").asText()).isEqualTo("ASSIGNED_EXTERNAL");
        assertThat(number("SELECT assigned_provider_id FROM maintenance_plan_item WHERE id=?", i)).isEqualTo(provider);
        assertThat(number("SELECT count(*) FROM user_notification WHERE notification_type='VENDOR_APPROVED' AND target_url=?", "/plans/"+p)).isGreaterThan(0);
    }
    @Test void mixedPreparedPlanWaitsForAllVendorsBeforeExecutionAndRemainsRevisable() {
        long p=create(free(), external(paid));
        approve(p);
        long contract=number("SELECT id FROM maintenance_plan_item WHERE plan_id=? AND equipment_id=?", p, eq);
        long external=number("SELECT id FROM maintenance_plan_item WHERE plan_id=? AND equipment_id=?", p, paid);
        assertThat(ok(send(HttpMethod.GET, "/api/plans/"+p, vtyt, null), 200).path("pendingVendorApproval").asBoolean()).isTrue();
        assertCode(send(HttpMethod.POST, "/api/plan-items/"+contract+"/executions", vtyt,
                Map.of("version", iv(contract), "planVersion", pv(p))), 409, "PLAN_VENDOR_APPROVAL_PENDING");
        assertThat(number("SELECT count(*) FROM maintenance_execution WHERE plan_item_id=?", contract)).isZero();
        decide(pending(external), iv(external), "REVISION_REQUIRED");
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("REVISION_REQUIRED");
        var revised=body(free(), external(paid));
        revised.put("version", pv(p));
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, revised), 200);
        approve(p);
        decide(pending(external), iv(external), "APPROVE");
        assertThat(ok(send(HttpMethod.GET, "/api/plans/"+p, vtyt, null), 200).path("pendingVendorApproval").asBoolean()).isFalse();
        ok(send(HttpMethod.POST, "/api/plan-items/"+contract+"/executions", vtyt,
                Map.of("version", iv(contract), "planVersion", pv(p))), 201);
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("IN_PROGRESS");
    }
    @Test void vendorRevisionReturnsWholePlanAndCancelsSiblingPendingRequests() {
        var p=create(external(paid), external(missing));
        approve(p);
        long i=item(p);
        long q=pending(i);
        decide(q, iv(i), "REVISION_REQUIRED");
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("REVISION_REQUIRED");
        assertThat(number("SELECT count(*) FROM approval_request WHERE status='PENDING' AND plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isZero();
        assertThat(number("SELECT count(*) FROM approval_request WHERE status='DRAFT' AND plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isEqualTo(2);
        var input=new HashMap<>(external(paid));
        input.put("proposedProviderId", provider2);
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, input)), 200);
        approve(p);
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("APPROVED");
        assertThat(number("SELECT count(*) FROM approval_action WHERE request_id=?", q)).isEqualTo(1);
    }
    @Test void planRevisionNotificationAndResubmitRelock() {
        var p=create(free());
        var s=submit(p);
        decide(s.path("approvalRequestId").asLong(), pv(p), "REVISION_REQUIRED");
        assertThat(number("SELECT count(*) FROM user_notification WHERE notification_type='PLAN_REVISION' AND target_url=?", "/plans/"+p+"/edit")).isGreaterThan(0);
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, external(eq))), 200);
        submit(p);
        assertCode(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, free())), 409, "PLAN_NOT_EDITABLE");
    }
    @Test void legacyPlanRemainsReadableAndApprovalDoesNotFabricateContent() {
        long p=create(Map.of("equipmentId", paid));
        jdbc.update("UPDATE maintenance_plan_item SET status='PENDING_PROPOSAL' WHERE plan_id=?", p);
        jdbc.update("UPDATE maintenance_plan SET status='SUBMITTED' WHERE id=?", p);
        long user=number("SELECT id FROM user_account WHERE username='demo_vtyt'");
        long q=jdbc.queryForObject("INSERT INTO approval_request(request_type,plan_id,status,created_by_user_id,submitted_at) VALUES('PLAN_APPROVAL',?,'PENDING',?,NOW()) RETURNING id", Long.class, p, user);
        decide(q, pv(p), "APPROVE");
        ok(send(HttpMethod.GET, "/api/plans/"+p+"/items", vtyt, null), 200);
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isZero();
    }
    @Test void stalePlanVersionRejectsWithoutProposalMutation() {
        var p=create(free());
        var body=edit(p, external(eq));
        body.put("version", pv(p)+5);
        assertCode(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, body), 409, "OPTIMISTIC_LOCK_CONFLICT");
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isZero();
    }
    @Test void staleItemVersionRollsBackMetadataAndProposal() {
        var p=create(free());
        var input=new HashMap<>(external(eq));
        input.put("version", iv(item(p))+9);
        assertCode(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, input)), 409, "OPTIMISTIC_LOCK_CONFLICT");
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?", p)).isEqualTo("UNDER_CONTRACT");
    }
    @Test void notificationFailureRollsBackSubmissionAndApprovalRequest() {
        var p=create(free());
        doThrow(new IllegalStateException("controlled notification failure")).when(notifications).save(any(vn.edu.medmaintenance.persistence.entity.UserNotification.class));
        ok(send(HttpMethod.POST, "/api/plans/"+p+"/submit", vtyt, Map.of("version", pv(p))), 500);
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("DRAFT");
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_id=?", p)).isZero();
    }
    @Test void notificationOwnershipPaginationReadAndReadAll() {
        var p=create(free());
        submit(p);
        var list=ok(send(HttpMethod.GET, "/api/notifications?size=1", bgd, null), 200);
        assertThat(list.path("size").asInt()).isEqualTo(1);
        long n=list.path("content").get(0).path("id").asLong();
        assertCode(send(HttpMethod.POST, "/api/notifications/"+n+"/read", vtyt, null), 404, "NOTIFICATION_NOT_FOUND");
        ok(send(HttpMethod.POST, "/api/notifications/"+n+"/read", bgd, null), 200);
        assertThat(ok(send(HttpMethod.GET, "/api/notifications/unread-count", bgd, null), 200).path("count").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(send(HttpMethod.POST, "/api/notifications/read-all", bgd, null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(ok(send(HttpMethod.GET, "/api/notifications/unread-count", bgd, null), 200).path("count").asLong()).isZero();
        assertThat(number("SELECT count(*) FROM user_notification n JOIN user_account u ON u.id=n.user_account_id WHERE u.username='demo_vtyt' AND n.read_at IS NOT NULL")).isZero();
    }
    @Test void notificationAnonymousDenied() {
        ok(send(HttpMethod.GET, "/api/notifications", null, null), 401);
    }
    @Test void unknownRemovedInJavaDatabaseAndApi() {
        assertThat(CoverageClassification.values()).containsExactly(CoverageClassification.FREE, CoverageClassification.NOT_FREE);
        assertThat(number("SELECT count(*) FROM maintenance_coverage WHERE classification NOT IN ('FREE','NOT_FREE')")).isZero();
        var input=new HashMap<>(free());
        input.put("classification", "UNKNOWN");
        ok(send(HttpMethod.POST, "/api/plans", vtyt, body(input)), 400);
    }
    @ParameterizedTest @ValueSource(strings= {
        "BGD", "KHOA", "ADMIN"
    }) void suggestionsAndPlanningAreVtytOnly(String role) {
        String token=role.equals("BGD")?bgd:role.equals("KHOA")?khoa:admin;
        ok(send(HttpMethod.GET, "/api/maintenance-suggestions", token, null), 403);
        ok(send(HttpMethod.POST, "/api/plans", token, body(free())), 403);
    }
    @Test void suggestionsAggregateHistoryCoverageOpenPlansAndInsufficientDates() {
        var result=ok(send(HttpMethod.GET, "/api/maintenance-suggestions?size=100", vtyt, null), 200);
        assertThat(result.path("content").size()).isEqualTo(number("SELECT count(*) FROM equipment WHERE active=true"));
        boolean insufficient=false;
        for (var row:result.path("content")) {
            assertThat(row.path("classification").asText()).isIn("FREE", "NOT_FREE");
            assertThat(row.path("equipmentCode").asText()).isNotBlank();
            assertThat(row.path("departmentName").asText()).isNotBlank();
            if (row.path("suggestedDate").isNull()) {
                insufficient=true;
                assertThat(row.path("suggestionBasis").asText()).contains("Chưa đủ dữ liệu");
            }
        }
        assertThat(insufficient).isTrue();
    }
    @Test void inferredIntervalUsesRealDates() {
        assertThat(MaintenanceSuggestionService.observedInterval(List.of(java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-01-11"), java.time.LocalDate.parse("2026-01-25")))).isEqualTo(12);
    }
    @Test void obsoleteRoutingAndManualSendingAreRejected() {
        var p=create(free());
        long i=item(p);
        assertCode(send(HttpMethod.POST, "/api/plan-items/"+i+"/route", vtyt, Map.of("version", iv(i), "coverageId", coverage)), 409, "PLANNING_WORKFLOW_REQUIRED");
        assertCode(send(HttpMethod.POST, "/api/plan-items/"+i+"/vendor-proposals", vtyt, Map.of("version", iv(i))), 409, "PLANNING_WORKFLOW_REQUIRED");
    }
    @Test void invalidFreeCoverageBlocksCreate() {
        var input=new HashMap<>(free());
        input.put("coverageId", number("SELECT id FROM maintenance_coverage WHERE equipment_id=?", paid));
        assertCode(send(HttpMethod.POST, "/api/plans", vtyt, body(input)), 409, "INVALID_FREE_COVERAGE");
    }
    @Test void freeCoverageDateBoundsInclusive() {
        var input=new HashMap<>(free());
        input.put("plannedDate", "2027-01-01");
        var b=body(input);
        b.put("periodStart", "2027-01-01");
        b.put("periodEnd", "2027-01-31");
        assertCode(send(HttpMethod.POST, "/api/plans", vtyt, b), 409, "INVALID_FREE_COVERAGE");
    }
    @Test void inactiveDirectorReceivesNothing() {
        var p=create(free());
        long recipient=number("SELECT id FROM user_account WHERE username='bgd_demo02'");
        jdbc.update("UPDATE user_account SET active=false WHERE id=?", recipient);
        try {
            var q=submit(p).path("approvalRequestId").asLong();
            assertThat(number("SELECT count(*) FROM user_notification WHERE user_account_id=? AND target_url=?", recipient, "/approvals/"+q)).isZero();
        }
        finally {
            jdbc.update("UPDATE user_account SET active=true WHERE id=?", recipient);
        }
    }
    @Test void historyProjectionInfersIntervalOnlyFromCompletedPassEvents() {
        long device=projectionEquipment();
        completedFixture(device, "2026-01-01");
        completedFixture(device, "2026-03-01");
        var row=suggestion(device);
        assertThat(row.path("lastMaintenanceDate").asText()).isEqualTo("2026-03-01");
        assertThat(row.path("suggestedDate").asText()).isEqualTo("2026-04-29");
        assertThat(row.path("suggestionBasis").asText()).contains("2 lần", "59 ngày");
        assertThat(row.path("classification").asText()).isEqualTo("NOT_FREE");
        assertThat(row.path("lastExternalProviderName").asText()).isNotBlank();
    }
    @Test void futurePlannedDateTakesPriorityOverInferredHistory() {
        long device=projectionEquipment();
        completedFixture(device, "2026-01-01");
        completedFixture(device, "2026-03-01");
        var input=new HashMap<>(external(device));
        input.put("plannedDate", "2026-11-16");
        var p=create(input);
        var row=suggestion(device);
        assertThat(row.path("suggestedDate").asText()).isEqualTo("2026-11-16");
        assertThat(row.path("suggestionBasis").asText()).contains("kế hoạch");
        assertThat(row.path("openPlanIds").toString()).contains(Long.toString(p));
    }
    private JsonNode suggestion(long device) {
        for (var row:ok(send(HttpMethod.GET, "/api/maintenance-suggestions?size=100", vtyt, null), 200).path("content"))if (row.path("equipmentId").asLong()==device)return row;
        throw new AssertionError("Missing suggestion");
    }
    private long projectionEquipment() {
        long device=jdbc.queryForObject("INSERT INTO equipment(department_id,equipment_code,name) SELECT department_id,?,'Thiết bị kiểm tra projection' FROM equipment WHERE id=? RETURNING id", Long.class, "SMOKE-V2-PROJECTION-"+UUID.randomUUID(), missing);
        projectionEquipment.add(device);
        return device;
    }
    private void completedFixture(long device, String date) {
        var p=create(external(device));
        long i=item(p);
        long vtytId=number("SELECT id FROM user_account WHERE username='demo_vtyt'");
        long khoaId=number("SELECT u.id FROM user_account u JOIN maintenance_plan_item i ON i.department_id_at_plan=u.department_id WHERE i.id=? AND u.role_code='KHOA_PHONG' ORDER BY u.id LIMIT 1", i);
        jdbc.update("UPDATE approval_request SET status='CANCELLED',resolved_at=NOW() WHERE plan_item_id=?", i);
        jdbc.update("UPDATE maintenance_plan SET status='CLOSED' WHERE id=?", p);
        jdbc.update("UPDATE maintenance_plan_item SET status='COMPLETED',assigned_provider_id=?,assignment_route='EXTERNAL_APPROVED' WHERE id=?", provider, i);
        long x=jdbc.queryForObject("INSERT INTO maintenance_execution(plan_item_id,provider_id,started_by_user_id,attempt_no,started_at,ended_at,result_note) VALUES(?,?,?,1,?::timestamptz,?::timestamptz,'Projection fixture') RETURNING id", Long.class, i, provider, vtytId, date+"T00:00:00Z", date+"T01:00:00Z");
        jdbc.update("INSERT INTO acceptance_record(execution_id,acceptance_type,result,observed_at,conclusion,recorded_by_user_id,department_confirmed_by_user_id,department_confirmed_at,vtyt_confirmed_by_user_id,vtyt_confirmed_at) VALUES(?,'HANDOVER_ACCEPTANCE','PASS',?::timestamptz,'Projection fixture',?,?,?::timestamptz,?,?::timestamptz)", x, date+"T02:00:00Z", vtytId, khoaId, date+"T02:00:00Z", vtytId, date+"T02:00:00Z");
    }
    @SafeVarargs private final long create(Map<String, Object>... inputs) {
        var b=body(inputs);
        b.put("title", "TEST-V2-"+UUID.randomUUID());
        var p=ok(send(HttpMethod.POST, "/api/plans", vtyt, b), 201).path("id").asLong();
        plans.add(p);
        return p;
    }
    @SafeVarargs private final HashMap<String, Object> body(Map<String, Object>... inputs) {
        return new HashMap<>(Map.of("title", "TEST-V2-FAILED", "periodStart", "2026-11-01", "periodEnd", "2026-11-30", "items", List.of(inputs)));
    }
    private Map<String, Object> free() {
        return Map.of("equipmentId", eq, "classification", "FREE", "coverageId", coverage);
    }
    private Map<String, Object> external(long e) {
        return Map.of("equipmentId", e, "classification", "NOT_FREE", "proposedProviderId", provider, "rationale", "Năng lực phù hợp", "warrantyImpactNote", "Giữ hồ sơ bảo hành");
    }
    private HashMap<String, Object> edit(long p, Map<String, Object> input) {
        var b=body(input);
        b.put("version", pv(p));
        return b;
    }
    private JsonNode submit(long p) {
        return ok(send(HttpMethod.POST, "/api/plans/"+p+"/submit", vtyt, Map.of("version", pv(p))), 200);
    }
    private JsonNode approve(long p) {
        var s=submit(p);
        return decide(s.path("approvalRequestId").asLong(), pv(p), "APPROVE");
    }
    private JsonNode decide(long q, int version, String outcome) {
        return ok(send(HttpMethod.POST, "/api/approvals/"+q+"/decision", bgd, Map.of("version", version, "outcome", outcome, "comment", "Điều chỉnh theo đánh giá")), 200);
    }
    private long pending(long i) {
        return number("SELECT id FROM approval_request WHERE plan_item_id=? AND status='PENDING'", i);
    }
    private long item(long p) {
        return number("SELECT id FROM maintenance_plan_item WHERE plan_id=? ORDER BY id LIMIT 1", p);
    }
    private int pv(long p) {
        return (int)number("SELECT version FROM maintenance_plan WHERE id=?", p);
    }
    private int iv(long i) {
        return (int)number("SELECT version FROM maintenance_plan_item WHERE id=?", i);
    }
    private long id(String code) {
        return number("SELECT id FROM equipment WHERE equipment_code=?", code);
    }
    private long number(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
    private String text(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
    private String login(String user, String role) {
        return ok(send(HttpMethod.POST, "/api/auth/login", null, Map.of("username", user, "password", System.getenv("DEMO_"+role+"_PASSWORD"))), 200).path("accessToken").asText();
    }
    private ResponseEntity<JsonNode> send(HttpMethod method, String path, String token, Object body) {
        var headers=new HttpHeaders();
        if (token!=null)headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode ok(ResponseEntity<JsonNode> r, int status) {
        assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(status);
        return r.getBody();
    }
    private void assertCode(ResponseEntity<JsonNode> r, int status, String code) {
        assertThat(ok(r, status).path("code").asText()).isEqualTo(code);
    }
}
