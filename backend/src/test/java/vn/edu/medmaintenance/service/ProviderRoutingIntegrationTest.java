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
    private java.util.Map<Long,String> fixtureStatuses;
    @org.junit.jupiter.api.BeforeEach void isolateOpenFixturePlans() { fixtureStatuses=PlanningTestData.archiveFixturePlans(jdbc); }
    @org.junit.jupiter.api.AfterEach void restoreOpenFixturePlans() { PlanningTestData.restoreFixturePlans(jdbc,fixtureStatuses); }

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
    @Test void missingClientClassificationIsDerivedBeforeSubmission() {
        var p=create(Map.of("equipmentId", eq));
        assertThat(submit(p).path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?",p)).isEqualTo("UNDER_CONTRACT");
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
    }) void editablePlanDerivesMethodFromChangedDate(String status) {
        var p=create(free());
        jdbc.update("UPDATE maintenance_plan SET status=? WHERE id=?", status, p);
        var input=new HashMap<>(external(eq));
        input.put("plannedDate", "2026-11-16");
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit(p, input)), 200);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?", p)).isEqualTo("UNDER_CONTRACT");
        assertThat(submit(p).path("status").asText()).isEqualTo("SUBMITTED");
    }
    @Test void validContractCannotBeOverriddenByClientClassification() {
        // The client cannot override a valid contract with NOT_FREE.
        var p=create(external(eq));
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?",p)).isEqualTo("UNDER_CONTRACT");
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)",p)).isZero();
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
    @Test void planApprovalIncludesPreparedProvidersWithoutAdditionalRequests() {
        long p=create(external(paid)), i=item(p);
        long before=ok(send(HttpMethod.GET,"/api/dashboard",bgd,null),200).path("summary").path("approvedThisMonth").asLong();
        var a=approve(p);
        assertThat(ok(send(HttpMethod.GET,"/api/dashboard",bgd,null),200).path("summary").path("approvedThisMonth").asLong()).isEqualTo(before+1);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("ASSIGNED_EXTERNAL");
        long q=number("SELECT id FROM approval_request WHERE plan_item_id=? AND status='DECIDED'", i);
        assertThat(text("SELECT rationale FROM approval_request WHERE id=?", q)).isEqualTo("Năng lực phù hợp");
        assertThat(number("SELECT assigned_provider_id FROM maintenance_plan_item WHERE id=?", i)).isEqualTo(provider);
        assertThat(text("SELECT assignment_route FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("EXTERNAL_APPROVED");
        assertThat(number("SELECT count(*) FROM approval_action v JOIN approval_action p ON p.request_id=? WHERE v.request_id=? AND v.actor_user_id=p.actor_user_id AND v.action_at=p.action_at AND v.outcome='APPROVE'", a.path("requestId").asLong(), q)).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM user_notification WHERE notification_type='VENDOR_PENDING' AND target_url=?", "/approvals/"+q)).isZero();
        assertCode(send(HttpMethod.POST, "/api/approvals/"+a.path("requestId").asLong()+"/decision", bgd, Map.of("version", pv(p), "outcome", "APPROVE")), 409, "APPROVAL_REQUEST_NOT_PENDING");
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id=?", i)).isEqualTo(1);
    }
    @Test void onePlanApprovalAllowsStartingContractAndExternalItemsTogether() {
        long p=create(free(), external(paid));
        approve(p);
        assertThat(ok(send(HttpMethod.GET, "/api/plans/"+p, vtyt, null), 200).path("pendingVendorApproval").asBoolean()).isFalse();
        ok(send(HttpMethod.POST, "/api/plans/"+p+"/start-maintenance", vtyt, Map.of("version", pv(p))), 200);
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("IN_PROGRESS");
        assertThat(number("SELECT count(*) FROM maintenance_execution x JOIN maintenance_plan_item i ON i.id=x.plan_item_id WHERE i.plan_id=?", p)).isEqualTo(2);
    }
    @Test void planRevisionKeepsAllProposalsEditableUntilResubmittedAndApproved() {
        long p=create(external(paid), external(missing));
        var q=submit(p).path("approvalRequestId").asLong();
        decide(q, pv(p), "REVISION_REQUIRED");
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("REVISION_REQUIRED");
        assertThat(number("SELECT count(*) FROM approval_request WHERE status='DRAFT' AND plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=? AND assigned_provider_id IS NOT NULL", p)).isZero();
        var replacement=new HashMap<>(external(paid));
        replacement.put("proposedProviderId", provider2);
        var edit=body(replacement, external(missing));edit.put("version", pv(p));
        ok(send(HttpMethod.PATCH, "/api/plans/"+p, vtyt, edit), 200);
        approve(p);
        assertThat(number("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=? AND status='ASSIGNED_EXTERNAL'", p)).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM approval_request WHERE status='PENDING' AND plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isZero();
    }
    @Test void notificationFailureRollsBackPlanAndAllProviderApprovals() {
        long p=create(external(paid), external(missing));
        long q=submit(p).path("approvalRequestId").asLong();
        doThrow(new IllegalStateException("controlled notification failure")).when(notifications).save(any(vn.edu.medmaintenance.persistence.entity.UserNotification.class));
        ok(send(HttpMethod.POST, "/api/approvals/"+q+"/decision", bgd, Map.of("version", pv(p), "outcome", "APPROVE")), 500);
        assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("SUBMITTED");
        assertThat(number("SELECT count(*) FROM maintenance_plan_item WHERE plan_id=? AND assigned_provider_id IS NOT NULL", p)).isZero();
        assertThat(number("SELECT count(*) FROM approval_request WHERE status='DRAFT' AND plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", p)).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM approval_action WHERE request_id IN (SELECT id FROM approval_request WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?))", p, p)).isZero();
    }
    @Test void providerDeactivatedAfterSubmissionPreventsPartialApproval() {
        long p=create(free(), external(paid));long q=submit(p).path("approvalRequestId").asLong();
        jdbc.update("UPDATE service_provider SET active=false WHERE id=?", provider);
        try {
            assertCode(send(HttpMethod.POST, "/api/approvals/"+q+"/decision", bgd, Map.of("version", pv(p), "outcome", "APPROVE")), 409, "PLAN_ITEM_INCOMPLETE");
            assertThat(text("SELECT status FROM maintenance_plan WHERE id=?", p)).isEqualTo("SUBMITTED");
            assertThat(number("SELECT count(*) FROM approval_action WHERE request_id=?", q)).isZero();
        } finally { jdbc.update("UPDATE service_provider SET active=true WHERE id=?", provider); }
    }
    @Test void migrationRepairsPreviouslyActivatedProposalsAndPreservesDecisionEvidence() throws Exception {
        long p=create(free(), external(paid));var decision=approve(p);
        long i=number("SELECT id FROM maintenance_plan_item WHERE plan_id=? AND equipment_id=?",p,paid);
        long q=number("SELECT id FROM approval_request WHERE plan_item_id=?",i);
        jdbc.update("DELETE FROM approval_action WHERE request_id=?",q);
        jdbc.update("UPDATE approval_request SET status='PENDING',resolved_at=NULL WHERE id=?",q);
        jdbc.update("UPDATE maintenance_plan_item SET status='WAITING_VENDOR_APPROVAL',assigned_provider_id=NULL,assignment_route=NULL WHERE id=?",i);
        jdbc.update("INSERT INTO status_history(plan_item_id,actor_user_id,old_state,new_state,action,reason,action_timestamp) SELECT ?,actor_user_id,'PENDING_PROPOSAL','WAITING_VENDOR_APPROVAL','ACTIVATE_PREPARED_VENDOR',?,action_at FROM approval_action WHERE request_id=?",i,"request="+q,decision.path("requestId").asLong());
        var path=java.nio.file.Path.of("../database/migrations/V013__approve_prepared_providers_with_plan.sql");
        if(!java.nio.file.Files.exists(path))path=java.nio.file.Path.of("database/migrations/V013__approve_prepared_providers_with_plan.sql");
        String migration=java.nio.file.Files.readString(path);
        jdbc.execute(migration);
        jdbc.execute(migration); // Reapplying cannot duplicate the inherited decision.
        assertThat(number("SELECT count(*) FROM approval_action WHERE request_id=?",q)).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM status_history WHERE plan_item_id=? AND action='ACTIVATE_PREPARED_VENDOR'",i)).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM status_history WHERE plan_item_id=? AND action='APPLY_PLAN_APPROVAL_TO_PROVIDER'",i)).isEqualTo(1);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE id=?",i)).isEqualTo("ASSIGNED_EXTERNAL");
        assertThat(ok(send(HttpMethod.GET,"/api/plans/"+p,vtyt,null),200).path("pendingVendorApproval").asBoolean()).isFalse();
        ok(send(HttpMethod.POST,"/api/plans/"+p+"/start-maintenance",vtyt,Map.of("version",pv(p))),200);
    }
    @ParameterizedTest @ValueSource(strings={"inactive", "revision", "laterProposal"})
    void migrationDoesNotApproveProposalsOutsideTheRecordedPlanDecision(String scenario) throws Exception {
        long p=create(external(paid));var decision=approve(p);long i=item(p);
        long q=number("SELECT id FROM approval_request WHERE plan_item_id=?",i);
        jdbc.update("DELETE FROM approval_action WHERE request_id=?",q);
        jdbc.update("UPDATE approval_request SET status='PENDING',resolved_at=NULL WHERE id=?",q);
        jdbc.update("UPDATE maintenance_plan_item SET status='WAITING_VENDOR_APPROVAL',assigned_provider_id=NULL,assignment_route=NULL WHERE id=?",i);
        jdbc.update("INSERT INTO status_history(plan_item_id,actor_user_id,old_state,new_state,action,reason,action_timestamp) SELECT ?,actor_user_id,'PENDING_PROPOSAL','WAITING_VENDOR_APPROVAL','ACTIVATE_PREPARED_VENDOR',?,action_at FROM approval_action WHERE request_id=?",i,"request="+q,decision.path("requestId").asLong());
        if(scenario.equals("inactive"))jdbc.update("UPDATE service_provider SET active=false WHERE id=?",provider);
        if(scenario.equals("revision"))jdbc.update("UPDATE maintenance_plan SET status='REVISION_REQUIRED' WHERE id=?",p);
        if(scenario.equals("laterProposal"))jdbc.update("UPDATE approval_request SET submitted_at=submitted_at+interval '1 minute' WHERE id=?",q);
        try {
            var path=java.nio.file.Path.of("../database/migrations/V013__approve_prepared_providers_with_plan.sql");
            if(!java.nio.file.Files.exists(path))path=java.nio.file.Path.of("database/migrations/V013__approve_prepared_providers_with_plan.sql");
            jdbc.execute(java.nio.file.Files.readString(path));
            assertThat(text("SELECT status FROM approval_request WHERE id=?",q)).isEqualTo("PENDING");
            assertThat(number("SELECT count(*) FROM approval_action WHERE request_id=?",q)).isZero();
            assertThat(number("SELECT count(*) FROM maintenance_plan_item WHERE id=? AND assigned_provider_id IS NULL",i)).isEqualTo(1);
        } finally { if(scenario.equals("inactive"))jdbc.update("UPDATE service_provider SET active=true WHERE id=?",provider); }
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
    @Test void incompleteExternalDraftRemainsReadableAndCannotSubmit() {
        long p=create(Map.of("equipmentId",paid));
        ok(send(HttpMethod.GET,"/api/plans/"+p+"/items",vtyt,null),200);
        assertCode(send(HttpMethod.POST,"/api/plans/"+p+"/submit",vtyt,Map.of("version",pv(p))),409,"PLAN_ITEM_INCOMPLETE");
        assertThat(number("SELECT count(*) FROM approval_request WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)",p)).isEqualTo(1);
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
                assertThat(row.path("suggestionBasis").asText()).contains("Cần cấu hình");
            }
        }
        assertThat(insufficient).isTrue();
    }
    @Test void obsoleteRoutingAndManualSendingAreRejected() {
        var p=create(free());
        long i=item(p);
        assertCode(send(HttpMethod.POST, "/api/plan-items/"+i+"/route", vtyt, Map.of("version", iv(i), "coverageId", coverage)), 409, "PLANNING_WORKFLOW_REQUIRED");
        assertCode(send(HttpMethod.POST, "/api/plan-items/"+i+"/vendor-proposals", vtyt, Map.of("version", iv(i))), 409, "PLANNING_WORKFLOW_REQUIRED");
    }
    @Test void clientCoverageOverrideIsIgnored() {
        var input=new HashMap<>(free());input.put("coverageId",number("SELECT id FROM maintenance_coverage WHERE equipment_id=?",paid));
        var p=create(input);
        assertThat(number("SELECT coverage_id FROM maintenance_plan_item WHERE plan_id=?",p)).isEqualTo(coverage);
    }
    @Test void expiredContractAutomaticallyBecomesExternal() {
        var b=body(free());b.put("periodStart","2027-01-01");b.put("periodEnd","2027-01-31");
        long p=ok(send(HttpMethod.POST,"/api/plans",vtyt,b),201).path("id").asLong();plans.add(p);
        assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?",p)).isEqualTo("PENDING_PROPOSAL");
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
    @Test void configuredCycleUsesCompletedPassEvents() {
        long device=projectionEquipment();
        jdbc.update("UPDATE equipment SET maintenance_enabled=true,maintenance_interval_value=6,maintenance_interval_unit='MONTH' WHERE id=?",device);
        completedFixture(device, "2026-01-01");
        completedFixture(device, "2026-03-01");
        var row=suggestion(device);
        assertThat(row.path("lastMaintenanceDate").asText()).isEqualTo("2026-03-01");
        assertThat(row.path("suggestedDate").asText()).isEqualTo("2026-09-01");
        assertThat(row.path("suggestionBasis").asText()).contains("Chu kỳ cấu hình");
        assertThat(row.path("classification").asText()).isEqualTo("NOT_FREE");
        assertThat(row.path("lastExternalProviderName").asText()).isNotBlank();
    }
    @Test void openPlanDatesDoNotReplacePeriodicDueDate() {
        long device=projectionEquipment();
        jdbc.update("UPDATE equipment SET maintenance_enabled=true,maintenance_interval_value=6,maintenance_interval_unit='MONTH' WHERE id=?",device);
        completedFixture(device, "2026-01-01");
        completedFixture(device, "2026-03-01");
        var input=new HashMap<>(external(device));
        input.put("plannedDate", "2026-11-16");
        var p=create(input);
        var row=suggestion(device);
        assertThat(row.path("suggestedDate").asText()).isEqualTo("2026-09-01");
        assertThat(row.path("suggestionBasis").asText()).contains("Chu kỳ cấu hình");
        assertThat(row.path("openPlanIds").toString()).contains(Long.toString(p));
    }
    @Test void warrantyExpiryDoesNotInvalidateSeparateMaintenanceContract() {
        var original=jdbc.queryForObject("SELECT warranty_expires_on FROM maintenance_coverage WHERE id=?", java.sql.Date.class, coverage);
        try {
            var input=Map.of("manufacturerProviderId", provider, "contracts", List.of(Map.of("id", coverage, "warrantyExpiresOn", "2026-11-01")));
            ok(send(HttpMethod.PUT, "/api/equipment/"+eq+"/warranty", vtyt, input), 200);
            var active=ok(send(HttpMethod.GET, "/api/equipment/"+eq+"/warranty?referenceDate=2026-11-01", bgd, null), 200);
            assertThat(active.path("contracts").get(0).path("warrantyStatus").asText()).isEqualTo("ACTIVE");
            assertThat(active.path("manufacturerProviderId").asLong()).isEqualTo(provider);
            var expired=ok(send(HttpMethod.GET, "/api/equipment/"+eq+"/warranty?referenceDate=2026-11-02", vtyt, null), 200);
            assertThat(expired.path("contracts").get(0).path("warrantyStatus").asText()).isEqualTo("EXPIRED");
            var invalid=new HashMap<>(free()); invalid.put("plannedDate", "2026-11-02");
            long p=create(invalid);
            assertThat(text("SELECT status FROM maintenance_plan_item WHERE plan_id=?",p)).isEqualTo("UNDER_CONTRACT");
            jdbc.update("UPDATE maintenance_plan SET status='CLOSED' WHERE id=?",p);
            var valid=new HashMap<>(free()); valid.put("plannedDate", "2026-11-01");
            create(valid);
        } finally {
            jdbc.update("UPDATE maintenance_coverage SET warranty_expires_on=? WHERE id=?", original, coverage);
            jdbc.update("UPDATE equipment SET manufacturer_provider_id=NULL WHERE id=?", eq);
        }
    }
    @Test void manufacturerChoiceIsSavedAndStillRequiresDirectorApproval() {
        jdbc.update("UPDATE equipment SET manufacturer_provider_id=? WHERE id=?", provider, paid);
        try {
            var input=new HashMap<>(external(paid)); input.put("serviceChoice", "MANUFACTURER");
            long p=create(input), i=item(p);
            assertThat(text("SELECT service_choice FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("MANUFACTURER");
            var row=ok(send(HttpMethod.GET, "/api/plans/"+p+"/items", vtyt, null), 200).path("content").get(0);
            assertThat(row.path("serviceChoice").asText()).isEqualTo("MANUFACTURER");
            approve(p);
            assertThat(text("SELECT status FROM maintenance_plan_item WHERE id=?", i)).isEqualTo("ASSIGNED_EXTERNAL");
            input.put("proposedProviderId", provider2);
            assertCode(send(HttpMethod.POST, "/api/plans", vtyt, body(input)), 409, "EQUIPMENT_IN_OPEN_PLAN");
        } finally { jdbc.update("UPDATE equipment SET manufacturer_provider_id=NULL WHERE id=?", paid); }
    }
    @Test void warrantyWritesAreScopedValidatedAndAtomic() {
        var invalid=Map.of("manufacturerProviderId", provider, "contracts", List.of(Map.of("id", coverage, "warrantyExpiresOn", "2026-11-01")));
        assertCode(send(HttpMethod.PUT, "/api/equipment/"+eq+"/warranty", bgd, invalid), 403, "ACCESS_DENIED");
        assertCode(send(HttpMethod.PUT, "/api/equipment/"+paid+"/warranty", vtyt, invalid), 409, "INVALID_WARRANTY_COVERAGE");
        assertThat(number("SELECT count(*) FROM equipment WHERE id=? AND manufacturer_provider_id IS NULL", paid)).isEqualTo(1);
        var dates=Map.of("contracts", List.of(Map.of("id", coverage, "warrantyExpiresOn", "2000-01-01")));
        assertCode(send(HttpMethod.PUT, "/api/equipment/"+eq+"/warranty", admin, dates), 409, "INVALID_WARRANTY_DATE");
        long other=number("SELECT e.id FROM equipment e WHERE e.department_id<>(SELECT department_id FROM user_account WHERE username='demo_khoa_noi') ORDER BY id LIMIT 1");
        assertCode(send(HttpMethod.GET, "/api/equipment/"+other+"/warranty", khoa, null), 403, "DEPARTMENT_SCOPE_VIOLATION");
        var noContract=ok(send(HttpMethod.GET, "/api/equipment/"+missing+"/warranty", vtyt, null), 200);
        assertThat(noContract.path("contracts").isEmpty()).isTrue();
    }
    @Test void unifiedCatalogIncludesInactiveDetailsAndFiltersSearch() {
        long device=projectionEquipment();
        jdbc.update("UPDATE equipment SET active=false, model='MODEL-QUICK', serial_number='SERIAL-UNIFIED', technical_spec='Thông số thử' WHERE id=?", device);
        var rows=ok(send(HttpMethod.GET, "/api/maintenance-suggestions?includeInactive=true&search=SERIAL-UNIFIED", vtyt, null), 200).path("content");
        assertThat(rows.size()).isEqualTo(1);
        assertThat(rows.get(0).path("equipmentId").asLong()).isEqualTo(device);
        assertThat(rows.get(0).path("active").asBoolean()).isFalse();
        assertThat(rows.get(0).path("model").asText()).isEqualTo("MODEL-QUICK");
        assertThat(rows.get(0).path("serialNumber").asText()).isEqualTo("SERIAL-UNIFIED");
        assertThat(rows.get(0).path("technicalSpec").asText()).isEqualTo("Thông số thử");
        assertThat(ok(send(HttpMethod.GET, "/api/maintenance-suggestions?search=SERIAL-UNIFIED", vtyt, null), 200).path("content").size()).isZero();
        assertThat(ok(send(HttpMethod.GET, "/api/maintenance-suggestions?active=false&search=SERIAL-UNIFIED", vtyt, null), 200).path("content").size()).isEqualTo(1);
        var filtered=ok(send(HttpMethod.GET, "/api/maintenance-suggestions?includeInactive=true&search=SERIAL-UNIFIED&referenceDate=2027-01-01", vtyt, null), 200).path("content").get(0);
        assertThat(filtered.path("referenceDate").asText()).isEqualTo("2027-01-01");
        assertThat(filtered.path("warrantyStatus").asText()).isEqualTo("UNKNOWN");
        assertThat(ok(send(HttpMethod.GET, "/api/maintenance-suggestions?includeInactive=true&search=%25", vtyt, null), 200).path("content").size()).isZero();
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
