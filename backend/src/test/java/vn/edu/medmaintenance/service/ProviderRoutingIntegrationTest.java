package vn.edu.medmaintenance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.medmaintenance.api.dto.request.RouteItemRequest;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.security.principal.AuthenticatedUser;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProviderRoutingIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired MaintenanceAssignmentService assignment;
    @MockitoSpyBean WorkflowHistory history;
    private final List<Long> plans = new ArrayList<>();
    private String vtyt, director, department, admin;
    private long freeEq, paidEq, unknownEq, noCoverageEq, freeCoverage, paidCoverage, unknownCoverage;
    private long provider1, provider2;

    @BeforeAll
    void setup() {
        vtyt = login("demo_vtyt", "VTYT");
        director = login("demo_bgd", "BGD");
        department = login("demo_khoa_noi", "KHOA");
        admin = login("demo_admin", "ADMIN");
        freeEq = equipment("DEMO-EQ-001");
        paidEq = equipment("DEMO-EQ-002");
        unknownEq = jdbc.queryForObject("SELECT equipment_id FROM maintenance_coverage WHERE classification='UNKNOWN' ORDER BY id LIMIT 1", Long.class);
        noCoverageEq = jdbc.queryForObject("SELECT e.id FROM equipment e WHERE NOT EXISTS (SELECT 1 FROM maintenance_coverage c WHERE c.equipment_id=e.id) ORDER BY e.id LIMIT 1", Long.class);
        freeCoverage = coverage(freeEq);
        paidCoverage = coverage(paidEq);
        unknownCoverage = coverage(unknownEq);
        provider1 = jdbc.queryForObject("SELECT id FROM service_provider WHERE active=true ORDER BY id LIMIT 1", Long.class);
        provider2 = jdbc.queryForObject("SELECT id FROM service_provider WHERE active=true AND id<>? ORDER BY id LIMIT 1", Long.class, provider1);
    }

    @AfterEach
    void cleanup() {
        for (long planId : plans) {
            jdbc.update("DELETE FROM approval_action WHERE request_id IN (SELECT r.id FROM approval_request r WHERE r.plan_id=? OR r.plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?))", planId, planId);
            jdbc.update("DELETE FROM approval_request WHERE plan_id=? OR plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", planId, planId);
            jdbc.update("DELETE FROM status_history WHERE plan_item_id IN (SELECT id FROM maintenance_plan_item WHERE plan_id=?)", planId);
            jdbc.update("DELETE FROM status_history WHERE plan_id=?", planId);
            jdbc.update("DELETE FROM maintenance_plan_item WHERE plan_id=?", planId);
            jdbc.update("DELETE FROM maintenance_plan WHERE id=?", planId);
        }
        plans.clear();
        assertThat(count("maintenance_plan")).isEqualTo(8);
        assertThat(count("maintenance_plan_item")).isEqualTo(52);
        assertThat(count("approval_request")).isEqualTo(19);
        assertThat(count("approval_action")).isEqualTo(15);
        assertThat(count("status_history")).isEqualTo(240);
    }

    @Test
    void freeCoverageAssignsOnlyContractedProviderWithOneHistory() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", freeEq);
        long item = item(plan, freeEq);
        int oldVersion = version(item);
        int requestsBefore = count("approval_request");
        long contracted = jdbc.queryForObject("SELECT provider_id FROM maintenance_coverage WHERE id=?", Long.class, freeCoverage);
        jdbc.update("UPDATE service_provider SET active=false WHERE id=?", contracted);
        try {
            fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                    Map.of("version", oldVersion, "coverageId", freeCoverage)), 409, "PROVIDER_INACTIVE");
        } finally {
            jdbc.update("UPDATE service_provider SET active=true WHERE id=?", contracted);
        }
        assertThat(itemStates(item)).containsExactly("PLANNED");
        JsonNode routed = ok(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                Map.of("version", oldVersion, "coverageId", freeCoverage)), 200);
        assertThat(routed.path("status").asText()).isEqualTo("UNDER_CONTRACT");
        assertThat(routed.path("assignmentRoute").asText()).isEqualTo("UNDER_CONTRACT");
        assertThat(routed.path("coverageId").asLong()).isEqualTo(freeCoverage);
        assertThat(routed.path("providerId").asLong()).isEqualTo(contracted);
        assertThat(routed.path("version").asInt()).isEqualTo(version(item));
        assertThat(count("approval_request")).isEqualTo(requestsBefore);
        assertThat(itemStates(item)).containsExactly("PLANNED", "UNDER_CONTRACT");
        JsonNode read = itemRead(plan, item);
        assertThat(read.path("status").asText()).isEqualTo("UNDER_CONTRACT");
        assertThat(read.path("assignmentRoute").asText()).isEqualTo("UNDER_CONTRACT");
        assertThat(read.path("assignedProviderId").asLong()).isEqualTo(contracted);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                Map.of("version", routed.path("version").asInt(), "coverageId", freeCoverage)),
                409, "PLAN_ITEM_STATE_CONFLICT");
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", vtyt,
                Map.of("version", routed.path("version").asInt())), 409, "PLAN_ITEM_STATE_CONFLICT");
    }

    @Test
    void notFreeDraftSubmitAndDirectorApprovalPreserveEvidence() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        JsonNode pending = route(item, paidCoverage);
        assertThat(pending.path("status").asText()).isEqualTo("PENDING_PROPOSAL");
        assertThat(pending.path("providerId").isNull()).isTrue();
        assertThat(pending.path("assignmentRoute").isNull()).isTrue();
        JsonNode draft = ok(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", vtyt,
                Map.of("version", pending.path("version").asInt())), 201);
        long requestId = draft.path("approvalRequestId").asLong();
        assertThat(draft.path("approvalStatus").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("version").asInt()).isEqualTo(version(item));
        assertThat(itemStates(item)).containsExactly("PLANNED", "PENDING_PROPOSAL");
        fail(send(HttpMethod.POST, "/api/vendor-proposals/" + requestId + "/submit", vtyt,
                Map.of("version", draft.path("version").asInt())), 400, "PROVIDER_REQUIRED");
        JsonNode submitted = ok(send(HttpMethod.POST, "/api/vendor-proposals/" + requestId + "/submit", vtyt,
                Map.of("version", draft.path("version").asInt(), "providerId", provider1,
                        "rationale", "Đối tác phù hợp kế hoạch", "warrantyImpactNote", "Đã xem xét bảo hành")), 200);
        assertThat(submitted.path("status").asText()).isEqualTo("WAITING_VENDOR_APPROVAL");
        assertThat(submitted.path("approvalStatus").asText()).isEqualTo("PENDING");
        assertThat(queueContains(requestId)).isTrue();
        assertThat(submitted.path("providerId").isNull()).isTrue();
        JsonNode approved = ok(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE")), 200);
        assertThat(approved.path("status").asText()).isEqualTo("ASSIGNED_EXTERNAL");
        assertThat(approved.path("assignmentRoute").asText()).isEqualTo("EXTERNAL_APPROVED");
        assertThat(approved.path("providerId").asLong()).isEqualTo(provider1);
        assertThat(approved.path("coverageId").asLong()).isEqualTo(paidCoverage);
        assertThat(approved.path("version").asInt()).isEqualTo(version(item));
        assertThat(approved.path("approvalActionId").asLong()).isPositive();
        assertThat(queueContains(requestId)).isFalse();
        assertThat(itemStates(item)).containsExactly("PLANNED", "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL", "ASSIGNED_EXTERNAL");
        assertThat(itemRead(plan, item).path("assignedProviderId").asLong()).isEqualTo(provider1);
        fail(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                Map.of("version", approved.path("version").asInt(), "outcome", "APPROVE")),
                409, "APPROVAL_REQUEST_NOT_PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class, requestId)).isEqualTo(1);
    }

    @Test
    void unknownAndNoCoverageNeverBecomeNotFree() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", unknownEq, noCoverageEq);
        long unknown = item(plan, unknownEq), absent = item(plan, noCoverageEq);
        fail(send(HttpMethod.POST, "/api/plan-items/" + unknown + "/route", vtyt,
                Map.of("version", version(unknown), "coverageId", unknownCoverage)), 409, "COVERAGE_UNKNOWN");
        fail(send(HttpMethod.POST, "/api/plan-items/" + absent + "/route", vtyt,
                Map.of("version", version(absent))), 409, "COVERAGE_REQUIRED");
        for (long id : List.of(unknown, absent)) {
            assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan_item WHERE id=?", String.class, id))
                    .isEqualTo("PLANNED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_request WHERE plan_item_id=?", Integer.class, id)).isZero();
            assertThat(itemStates(id)).containsExactly("PLANNED");
        }
    }

    @Test
    void expiredCoverageAndWrongEquipmentCoverageAreRejected() {
        long current = approvedPlan("2026-11-01", "2026-11-30", freeEq);
        long item = item(current, freeEq);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                Map.of("version", version(item), "coverageId", paidCoverage)),
                409, "COVERAGE_EQUIPMENT_MISMATCH");
        long future = approvedPlan("2027-02-01", "2027-02-28", freeEq);
        long futureItem = item(future, freeEq);
        fail(send(HttpMethod.POST, "/api/plan-items/" + futureItem + "/route", vtyt,
                Map.of("version", version(futureItem), "coverageId", freeCoverage)),
                409, "COVERAGE_NOT_APPLICABLE");
        assertThat(itemStates(item)).containsExactly("PLANNED");
        assertThat(itemStates(futureItem)).containsExactly("PLANNED");
    }

    @Test
    void coverageVerifierMustBeVtyt() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", freeEq);
        long item = item(plan, freeEq);
        long originalVerifier = jdbc.queryForObject("SELECT verified_by_user_id FROM maintenance_coverage WHERE id=?",
                Long.class, freeCoverage);
        long adminId = jdbc.queryForObject("SELECT id FROM user_account WHERE username='demo_admin'", Long.class);
        jdbc.update("UPDATE maintenance_coverage SET verified_by_user_id=? WHERE id=?", adminId, freeCoverage);
        try {
            fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                    Map.of("version", version(item), "coverageId", freeCoverage)),
                    409, "COVERAGE_UNVERIFIED");
        } finally {
            jdbc.update("UPDATE maintenance_coverage SET verified_by_user_id=? WHERE id=?",
                    originalVerifier, freeCoverage);
        }
        assertThat(itemStates(item)).containsExactly("PLANNED");
    }

    @Test
    void plannedItemDateOverridesPlanStartForCoverageApplicability() {
        JsonNode created = ok(send(HttpMethod.POST, "/api/plans", vtyt,
                Map.of("title", "TEST-ROUTING-DATE-" + UUID.randomUUID(),
                        "periodStart", "2026-12-01", "periodEnd", "2027-01-31",
                        "items", List.of(Map.of("equipmentId", freeEq, "plannedDate", "2027-01-10")))), 201);
        long plan = created.path("id").asLong();
        plans.add(plan);
        JsonNode submitted = ok(send(HttpMethod.POST, "/api/plans/" + plan + "/submit", vtyt,
                Map.of("version", created.path("version").asInt())), 200);
        ok(send(HttpMethod.POST, "/api/approvals/" + submitted.path("approvalRequestId").asLong() + "/decision",
                director, Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE")), 200);
        long item = item(plan, freeEq);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                Map.of("version", version(item), "coverageId", freeCoverage)), 409, "COVERAGE_NOT_APPLICABLE");
        assertThat(itemStates(item)).containsExactly("PLANNED");
    }

    @Test
    void inactiveProposedProviderBlocksDirectorDecisionWithoutPartialWrites() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        JsonNode routed = route(item, paidCoverage);
        JsonNode draft = draft(item, routed.path("version").asInt(), provider1);
        JsonNode sent = submit(draft.path("approvalRequestId").asLong(), draft.path("version").asInt());
        long requestId = sent.path("approvalRequestId").asLong();
        jdbc.update("UPDATE service_provider SET active=false WHERE id=?", provider1);
        try {
            fail(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                    Map.of("version", sent.path("version").asInt(), "outcome", "APPROVE")),
                    409, "PROVIDER_INACTIVE");
        } finally {
            jdbc.update("UPDATE service_provider SET active=true WHERE id=?", provider1);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM approval_request WHERE id=?", String.class, requestId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class, requestId))
                .isZero();
        assertThat(itemStates(item)).containsExactly("PLANNED", "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL");
    }

    @Test
    void routeRequiresApprovedPlanForDraftSubmittedAndRevision() {
        long plan = draftPlan("2026-11-01", "2026-11-30", freeEq);
        long item = item(plan, freeEq);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                Map.of("version", version(item), "coverageId", freeCoverage)), 409, "PLAN_NOT_APPROVED");
        JsonNode detail = ok(send(HttpMethod.GET, "/api/plans/" + plan, vtyt, null), 200);
        JsonNode submitted = ok(send(HttpMethod.POST, "/api/plans/" + plan + "/submit", vtyt,
                Map.of("version", detail.path("version").asInt())), 200);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                Map.of("version", version(item), "coverageId", freeCoverage)), 409, "PLAN_NOT_APPROVED");
        ok(send(HttpMethod.POST, "/api/approvals/" + submitted.path("approvalRequestId").asLong() + "/decision", director,
                Map.of("version", submitted.path("version").asInt(), "outcome", "REVISION_REQUIRED", "comment", "Chỉnh kế hoạch")), 200);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", vtyt,
                Map.of("version", version(item), "coverageId", freeCoverage)), 409, "PLAN_NOT_APPROVED");
        assertThat(itemStates(item)).containsExactly("PLANNED");
    }

    @Test
    void vendorRevisionCreatesNewRequestAndKeepsFirstDecision() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        JsonNode routed = route(item, paidCoverage);
        JsonNode draft1 = draft(item, routed.path("version").asInt(), provider1);
        JsonNode sent1 = submit(draft1.path("approvalRequestId").asLong(), draft1.path("version").asInt());
        long request1 = sent1.path("approvalRequestId").asLong();
        fail(send(HttpMethod.POST, "/api/approvals/" + request1 + "/decision", director,
                Map.of("version", sent1.path("version").asInt(), "outcome", "REVISION_REQUIRED")),
                400, "REVISION_COMMENT_REQUIRED");
        JsonNode revised = ok(send(HttpMethod.POST, "/api/approvals/" + request1 + "/decision", director,
                Map.of("version", sent1.path("version").asInt(), "outcome", "REVISION_REQUIRED",
                        "comment", "Chọn đơn vị khác")), 200);
        assertThat(revised.path("status").asText()).isEqualTo("PENDING_PROPOSAL");
        assertThat(revised.path("providerId").isNull()).isTrue();
        fail(send(HttpMethod.POST, "/api/approvals/" + request1 + "/decision", director,
                Map.of("version", revised.path("version").asInt(), "outcome", "APPROVE")),
                409, "APPROVAL_REQUEST_NOT_PENDING");
        JsonNode draft2 = draft(item, revised.path("version").asInt(), provider2);
        JsonNode sent2 = submit(draft2.path("approvalRequestId").asLong(), draft2.path("version").asInt());
        long request2 = sent2.path("approvalRequestId").asLong();
        assertThat(request2).isNotEqualTo(request1);
        JsonNode approved = ok(send(HttpMethod.POST, "/api/approvals/" + request2 + "/decision", director,
                Map.of("version", sent2.path("version").asInt(), "outcome", "APPROVE")), 200);
        assertThat(approved.path("status").asText()).isEqualTo("ASSIGNED_EXTERNAL");
        assertThat(approved.path("providerId").asLong()).isEqualTo(provider2);
        assertThat(jdbc.queryForList("SELECT status FROM approval_request WHERE plan_item_id=? ORDER BY id", String.class, item))
                .containsExactly("DECIDED", "DECIDED");
        assertThat(jdbc.queryForList("SELECT a.outcome FROM approval_action a JOIN approval_request r ON a.request_id=r.id WHERE r.plan_item_id=? ORDER BY a.id", String.class, item))
                .containsExactly("REVISION_REQUIRED", "APPROVE");
        assertThat(itemStates(item)).containsExactly("PLANNED", "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL",
                "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL", "ASSIGNED_EXTERNAL");
        assertThat(jdbc.queryForObject("SELECT reason FROM status_history WHERE plan_item_id=? AND action='RECORD_VENDOR_REVISION'", String.class, item))
                .isEqualTo("Chọn đơn vị khác");
    }

    @Test
    void vendorDecisionRequiresApprovedPlanAndWaitingItem() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        JsonNode routed = route(item, paidCoverage);
        JsonNode draft = draft(item, routed.path("version").asInt(), provider1);
        JsonNode sent = submit(draft.path("approvalRequestId").asLong(), draft.path("version").asInt());
        long requestId = sent.path("approvalRequestId").asLong();
        Map<String, Object> decision = Map.of("version", sent.path("version").asInt(), "outcome", "APPROVE");
        jdbc.update("UPDATE maintenance_plan SET status='DRAFT' WHERE id=?", plan);
        try {
            fail(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director, decision),
                    409, "PLAN_NOT_APPROVED");
        } finally {
            jdbc.update("UPDATE maintenance_plan SET status='APPROVED' WHERE id=?", plan);
        }
        jdbc.update("UPDATE maintenance_plan_item SET status='PENDING_PROPOSAL' WHERE id=?", item);
        try {
            fail(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director, decision),
                    409, "PLAN_ITEM_STATE_CONFLICT");
        } finally {
            jdbc.update("UPDATE maintenance_plan_item SET status='WAITING_VENDOR_APPROVAL' WHERE id=?", item);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class, requestId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM approval_request WHERE id=?", String.class, requestId))
                .isEqualTo("PENDING");
        assertThat(itemStates(item)).containsExactly("PLANNED", "PENDING_PROPOSAL", "WAITING_VENDOR_APPROVAL");
    }

    @Test
    void vendorSubmissionRejectsInvalidProviderDuplicateAndWrongType() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        JsonNode routed = route(item, paidCoverage);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", vtyt,
                Map.of("version", routed.path("version").asInt(), "providerId", 999999999L)),
                404, "PROVIDER_NOT_FOUND");
        jdbc.update("UPDATE service_provider SET active=false WHERE id=?", provider1);
        try {
            fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", vtyt,
                    Map.of("version", routed.path("version").asInt(), "providerId", provider1)),
                    409, "PROVIDER_INACTIVE");
        } finally {
            jdbc.update("UPDATE service_provider SET active=true WHERE id=?", provider1);
        }
        JsonNode draft = draft(item, routed.path("version").asInt(), provider1);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", vtyt,
                Map.of("version", routed.path("version").asInt())), 409, "VENDOR_DRAFT_EXISTS");
        long planRequest = jdbc.queryForObject("SELECT id FROM approval_request WHERE plan_id=?", Long.class, plan);
        fail(send(HttpMethod.POST, "/api/vendor-proposals/" + planRequest + "/submit", vtyt,
                Map.of("version", draft.path("version").asInt())), 409, "APPROVAL_REQUEST_WRONG_TYPE");
        JsonNode sent = submit(draft.path("approvalRequestId").asLong(), draft.path("version").asInt());
        fail(send(HttpMethod.POST, "/api/vendor-proposals/" + draft.path("approvalRequestId").asLong() + "/submit", vtyt,
                Map.of("version", sent.path("version").asInt())), 409, "VENDOR_PROPOSAL_NOT_DRAFT");
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", vtyt,
                Map.of("version", sent.path("version").asInt())), 409, "PENDING_VENDOR_APPROVAL_EXISTS");
    }

    @Test
    void staleItemVersionLeavesNoProposalOrAssignment() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        int before = version(item);
        JsonNode routed = route(item, paidCoverage);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", vtyt,
                Map.of("version", before, "providerId", provider1)), 409, "OPTIMISTIC_LOCK_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_request WHERE plan_item_id=?", Integer.class, item)).isZero();
        assertThat(itemStates(item)).containsExactly("PLANNED", "PENDING_PROPOSAL");
        JsonNode draft = draft(item, routed.path("version").asInt(), provider1);
        JsonNode sent = submit(draft.path("approvalRequestId").asLong(), draft.path("version").asInt());
        fail(send(HttpMethod.POST, "/api/approvals/" + sent.path("approvalRequestId").asLong() + "/decision", director,
                Map.of("version", routed.path("version").asInt(), "outcome", "APPROVE")),
                409, "OPTIMISTIC_LOCK_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class,
                sent.path("approvalRequestId").asLong())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM approval_request WHERE id=?", String.class,
                sent.path("approvalRequestId").asLong())).isEqualTo("PENDING");
    }

    @Test
    void failedHistoryInsertRollsBackVendorDecisionAndAssignment() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        JsonNode routed = route(item, paidCoverage);
        JsonNode draft = draft(item, routed.path("version").asInt(), provider1);
        JsonNode sent = submit(draft.path("approvalRequestId").asLong(), draft.path("version").asInt());
        long requestId = sent.path("approvalRequestId").asLong();
        int beforeHistory = itemStates(item).size();
        doThrow(new IllegalStateException("test-only history failure")).when(history).itemTransition(
                any(), any(), eq("WAITING_VENDOR_APPROVAL"), eq("ASSIGNED_EXTERNAL"),
                eq("RECORD_VENDOR_APPROVAL"), isNull(), any());
        try {
            fail(send(HttpMethod.POST, "/api/approvals/" + requestId + "/decision", director,
                    Map.of("version", sent.path("version").asInt(), "outcome", "APPROVE")),
                    500, "INTERNAL_ERROR");
        } finally {
            reset(history);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM approval_request WHERE id=?", String.class, requestId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_action WHERE request_id=?", Integer.class, requestId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM maintenance_plan_item WHERE id=?", String.class, item))
                .isEqualTo("WAITING_VENDOR_APPROVAL");
        assertThat(jdbc.queryForObject("SELECT assigned_provider_id FROM maintenance_plan_item WHERE id=?", Long.class, item))
                .isNull();
        assertThat(itemStates(item)).hasSize(beforeHistory);
    }

    @Test
    void rolesAndDirectServiceGuardApplyToAllVendorCommands() {
        long plan = approvedPlan("2026-11-01", "2026-11-30", paidEq);
        long item = item(plan, paidEq);
        Map<String, Object> route = Map.of("version", version(item), "coverageId", paidCoverage);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", null, route), 401, "AUTHENTICATION_REQUIRED");
        for (String token : List.of(director, department, admin))
            fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/route", token, route), 403, "ACCESS_DENIED");
        JsonNode routed = route(item, paidCoverage);
        fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", null,
                Map.of("version", routed.path("version").asInt())), 401, "AUTHENTICATION_REQUIRED");
        for (String token : List.of(director, department, admin))
            fail(send(HttpMethod.POST, "/api/plan-items/" + item + "/vendor-proposals", token,
                    Map.of("version", routed.path("version").asInt())), 403, "ACCESS_DENIED");
        JsonNode draft = draft(item, routed.path("version").asInt(), provider1);
        fail(send(HttpMethod.POST, "/api/vendor-proposals/" + draft.path("approvalRequestId").asLong() + "/submit", null,
                Map.of("version", draft.path("version").asInt())), 401, "AUTHENTICATION_REQUIRED");
        for (String token : List.of(director, department, admin))
            fail(send(HttpMethod.POST, "/api/vendor-proposals/" + draft.path("approvalRequestId").asLong() + "/submit", token,
                    Map.of("version", draft.path("version").asInt())), 403, "ACCESS_DENIED");
        JsonNode sent = submit(draft.path("approvalRequestId").asLong(), draft.path("version").asInt());
        fail(send(HttpMethod.POST, "/api/approvals/" + sent.path("approvalRequestId").asLong() + "/decision", null,
                Map.of("version", sent.path("version").asInt(), "outcome", "APPROVE")), 401, "AUTHENTICATION_REQUIRED");
        for (String token : List.of(vtyt, department, admin))
            fail(send(HttpMethod.POST, "/api/approvals/" + sent.path("approvalRequestId").asLong() + "/decision", token,
                    Map.of("version", sent.path("version").asInt(), "outcome", "APPROVE")), 403, "ACCESS_DENIED");
        long userId = jdbc.queryForObject("SELECT id FROM user_account WHERE username='demo_khoa_noi'", Long.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, "demo_khoa_noi", UserRole.KHOA_PHONG, 1L), null));
        try {
            assertThatThrownBy(() -> assignment.route(item, new RouteItemRequest(version(item), paidCoverage)))
                    .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("BUSINESS_ACCESS_DENIED");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long draftPlan(String start, String end, long... equipmentIds) {
        List<Map<String, Object>> selections = new ArrayList<>();
        for (long id : equipmentIds) selections.add(Map.of("equipmentId", id));
        JsonNode created = ok(send(HttpMethod.POST, "/api/plans", vtyt,
                Map.of("title", "TEST-ROUTING-" + UUID.randomUUID(), "periodStart", start,
                        "periodEnd", end, "items", selections)), 201);
        long planId = created.path("id").asLong();
        plans.add(planId);
        return planId;
    }

    private long approvedPlan(String start, String end, long... equipmentIds) {
        long id = draftPlan(start, end, equipmentIds);
        JsonNode detail = ok(send(HttpMethod.GET, "/api/plans/" + id, vtyt, null), 200);
        JsonNode submitted = ok(send(HttpMethod.POST, "/api/plans/" + id + "/submit", vtyt,
                Map.of("version", detail.path("version").asInt())), 200);
        ok(send(HttpMethod.POST, "/api/approvals/" + submitted.path("approvalRequestId").asLong() + "/decision", director,
                Map.of("version", submitted.path("version").asInt(), "outcome", "APPROVE")), 200);
        return id;
    }

    private JsonNode route(long itemId, long coverageId) {
        return ok(send(HttpMethod.POST, "/api/plan-items/" + itemId + "/route", vtyt,
                Map.of("version", version(itemId), "coverageId", coverageId)), 200);
    }

    private JsonNode draft(long itemId, int version, long providerId) {
        return ok(send(HttpMethod.POST, "/api/plan-items/" + itemId + "/vendor-proposals", vtyt,
                Map.of("version", version, "providerId", providerId,
                        "rationale", "Đề xuất theo năng lực kỹ thuật", "warrantyImpactNote", "Đã rà soát")), 201);
    }

    private JsonNode submit(long requestId, int version) {
        return ok(send(HttpMethod.POST, "/api/vendor-proposals/" + requestId + "/submit", vtyt,
                Map.of("version", version)), 200);
    }

    private long equipment(String code) {
        return jdbc.queryForObject("SELECT id FROM equipment WHERE equipment_code=?", Long.class, code);
    }
    private long coverage(long equipmentId) {
        return jdbc.queryForObject("SELECT id FROM maintenance_coverage WHERE equipment_id=? ORDER BY id LIMIT 1", Long.class, equipmentId);
    }
    private long item(long planId, long equipmentId) {
        return jdbc.queryForObject("SELECT id FROM maintenance_plan_item WHERE plan_id=? AND equipment_id=?", Long.class,
                planId, equipmentId);
    }
    private int version(long itemId) {
        return jdbc.queryForObject("SELECT version FROM maintenance_plan_item WHERE id=?", Integer.class, itemId);
    }
    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
    private List<String> itemStates(long itemId) {
        return jdbc.queryForList("SELECT new_state FROM status_history WHERE plan_item_id=? ORDER BY id", String.class, itemId);
    }
    private JsonNode itemRead(long planId, long itemId) {
        JsonNode page = ok(send(HttpMethod.GET, "/api/plans/" + planId + "/items?size=100", vtyt, null), 200);
        for (JsonNode row : page.path("content")) if (row.path("id").asLong() == itemId) return row;
        throw new AssertionError("Missing item in GET response");
    }
    private boolean queueContains(long requestId) {
        JsonNode page = ok(send(HttpMethod.GET, "/api/approvals/pending?requestType=VENDOR_SELECTION&size=100", director,
                null), 200);
        for (JsonNode row : page.path("content")) if (row.path("id").asLong() == requestId) return true;
        return false;
    }
    private String login(String name, String role) {
        String password = System.getenv("DEMO_" + role + "_PASSWORD");
        assertThat(password).isNotBlank();
        return ok(send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("username", name, "password", password)), 200).path("accessToken").asText();
    }
    private ResponseEntity<JsonNode> send(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
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
        assertThat(body.path("fieldErrors").isArray()).isTrue();
        assertThat(body.toString()).doesNotContain("Exception", "java.lang", "passwordHash", "stackTrace");
    }
}
