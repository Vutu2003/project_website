package vn.edu.medmaintenance.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.*;

@SpringBootTest
@Transactional
class RepositoryIntegrationTest {
    private static final Sort EQUIPMENT_ORDER = Sort.by("equipmentCode").ascending().and(Sort.by("id"));
    private static final Sort ITEM_HISTORY_ORDER = Sort.by("plan.periodStart").descending()
            .and(Sort.by("plan.id").descending()).and(Sort.by("id").descending());
    private static final Sort REQUEST_ORDER = Sort.by("submittedAt").ascending().and(Sort.by("id"));

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DepartmentRepository departments;
    @Autowired private UserAccountRepository users;
    @Autowired private EquipmentRepository equipment;
    @Autowired private ServiceProviderRepository providers;
    @Autowired private MaintenanceCoverageRepository coverage;
    @Autowired private MaintenancePlanRepository plans;
    @Autowired private MaintenancePlanItemRepository items;
    @Autowired private ApprovalRequestRepository requests;
    @Autowired private ApprovalActionRepository actions;
    @Autowired private MaintenanceExecutionRepository executions;
    @Autowired private MaintenanceProgressLogRepository logs;
    @Autowired private AcceptanceRecordRepository acceptances;
    @Autowired private MaintenanceReportRepository reports;
    @Autowired private StatusHistoryRepository history;

    @Test
    void allFourteenRepositoriesLoadAndMasterLookupsUseSeedData() {
        for (Class<?> type : List.of(DepartmentRepository.class, UserAccountRepository.class,
                EquipmentRepository.class, ServiceProviderRepository.class, MaintenanceCoverageRepository.class,
                MaintenancePlanRepository.class, MaintenancePlanItemRepository.class, ApprovalRequestRepository.class,
                ApprovalActionRepository.class, MaintenanceExecutionRepository.class,
                MaintenanceProgressLogRepository.class, AcceptanceRecordRepository.class,
                MaintenanceReportRepository.class, StatusHistoryRepository.class)) {
            assertThat(context.getBean(type)).isNotNull();
        }
        assertThat(departments.count()).isEqualTo(8);
        assertThat(users.count()).isEqualTo(15);
        assertThat(equipment.count()).isEqualTo(40);
        assertThat(providers.count()).isEqualTo(7);
        assertThat(departments.findByCode("HOI_SUC")).isPresent();
        assertThat(users.findByRoleCodeOrderByUsernameAsc(UserRole.BAN_GIAM_DOC)).hasSize(2);
        UserAccount departmentUser = users.findByDepartment_IdOrderByUsernameAsc(
                departments.findByCode("HOI_SUC").orElseThrow().getId()).get(0);
        assertThat(users.findByUsername(departmentUser.getUsername())).contains(departmentUser);
        assertThat(providers.findByActiveTrueOrderByNameAsc()).hasSize(7)
                .extracting(ServiceProvider::getName).isSorted();
        assertThat(equipment.findByEquipmentCode("DEMO-EQ-004")).isPresent();
    }

    @Test
    void equipmentByDepartmentAndPaginationHaveDeterministicOrderAndFetchDepartment() {
        Department hoiSuc = departments.findByCode("HOI_SUC").orElseThrow();
        var first = equipment.findByDepartment_Id(hoiSuc.getId(), PageRequest.of(0, 3, EQUIPMENT_ORDER));
        var second = equipment.findByDepartment_Id(hoiSuc.getId(), PageRequest.of(1, 3, EQUIPMENT_ORDER));
        assertThat(first.getTotalElements()).isEqualTo(8);
        assertThat(first.getContent()).hasSize(3);
        assertThat(second.getContent()).hasSize(3);
        assertThat(first.getContent()).extracting(Equipment::getEquipmentCode)
                .isSorted().doesNotContainAnyElementsOf(second.getContent().stream()
                        .map(Equipment::getEquipmentCode).toList());
        assertThat(first.getContent()).allSatisfy(e -> {
            assertThat(Hibernate.isInitialized(e.getDepartment())).isTrue();
            assertThat(e.getDepartment().getCode()).isEqualTo("HOI_SUC");
        });
        assertThat(equipment.findAllBy(PageRequest.of(0, 20, EQUIPMENT_ORDER)).getTotalElements()).isEqualTo(40);
        assertThat(equipment.findByActiveTrue(PageRequest.of(0, 20, EQUIPMENT_ORDER)).getTotalElements()).isEqualTo(40);
    }

    @Test
    void planAndPlanItemQueriesSupportStatusDetailAndEquipmentHistory() {
        var first = plans.findAllBy(PageRequest.of(0, 3,
                Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        var second = plans.findAllBy(PageRequest.of(1, 3,
                Sort.by("createdAt").descending().and(Sort.by("id").descending())));
        assertThat(first.getTotalElements()).isEqualTo(8);
        assertThat(first.getContent()).hasSize(3);
        assertThat(second.getContent()).hasSize(3);
        assertThat(first.getContent()).extracting(MaintenancePlan::getId)
                .doesNotContainAnyElementsOf(second.getContent().stream().map(MaintenancePlan::getId).toList());
        assertThat(plans.findByStatus(PlanStatus.APPROVED, PageRequest.of(0, 5,
                Sort.by("periodStart").descending().and(Sort.by("id")))).getTotalElements()).isEqualTo(1);
        Long planId = first.getContent().get(0).getId();
        assertThat(Hibernate.isInitialized(plans.findWithCreatorById(planId).orElseThrow().getCreatedByUser())).isTrue();

        Equipment device = equipment.findByEquipmentCode("DEMO-EQ-004").orElseThrow();
        var deviceHistory = items.findByEquipment_Id(device.getId(), PageRequest.of(0, 1, ITEM_HISTORY_ORDER));
        assertThat(deviceHistory.getTotalElements()).isEqualTo(2);
        assertThat(deviceHistory.getContent()).hasSize(1);
        var secondHistory = items.findByEquipment_Id(device.getId(), PageRequest.of(1, 1, ITEM_HISTORY_ORDER));
        assertThat(deviceHistory.getContent().get(0).getId()).isNotEqualTo(secondHistory.getContent().get(0).getId());
        assertThat(deviceHistory.getContent().get(0).getPlan().getPeriodStart())
                .isAfter(secondHistory.getContent().get(0).getPlan().getPeriodStart());
        assertThat(Hibernate.isInitialized(deviceHistory.getContent().get(0).getPlan())).isTrue();
        assertThat(Set.of(deviceHistory.getContent().get(0).getStatus(), secondHistory.getContent().get(0).getStatus()))
                .containsExactlyInAnyOrder(PlanItemStatus.COMPLETED, PlanItemStatus.REPAIR_REQUIRED);

        MaintenancePlanItem completed = deviceHistory.getContent().get(0).getStatus() == PlanItemStatus.COMPLETED
                ? deviceHistory.getContent().get(0) : secondHistory.getContent().get(0);
        var planItems = items.findByPlan_Id(completed.getPlan().getId(),
                PageRequest.of(0, 20, Sort.by("id")));
        assertThat(planItems.getTotalElements()).isGreaterThan(0);
        assertThat(planItems.getContent()).allSatisfy(i -> assertThat(Hibernate.isInitialized(i.getEquipment())).isTrue());
        assertThat(items.findByPlan_IdAndStatus(completed.getPlan().getId(), PlanItemStatus.COMPLETED,
                PageRequest.of(0, 20, Sort.by("id"))).getTotalElements()).isGreaterThan(0);
        assertThat(items.findByPlan_IdAndEquipment_Id(completed.getPlan().getId(), device.getId()))
                .contains(completed);
        assertThat(items.findByStatus(PlanItemStatus.PLANNED,
                PageRequest.of(0, 20, Sort.by("id"))).getTotalElements()).isEqualTo(15);
        assertThat(items.findByDepartmentAtPlan_Id(completed.getDepartmentAtPlan().getId(),
                PageRequest.of(0, 20, Sort.by("id"))).getTotalElements()).isGreaterThan(0);
    }

    @Test
    void coverageQueriesReturnEvidenceWithoutInventingRoutingDecisions() {
        Equipment freeDevice = equipment.findByEquipmentCode("DEMO-EQ-011").orElseThrow();
        var freeEvidence = coverage.findEvidenceForEquipment(freeDevice.getId());
        assertThat(freeEvidence).isNotEmpty();
        assertThat(freeEvidence.get(0).getClassification()).isEqualTo(CoverageClassification.FREE);
        assertThat(coverage.findDateApplicableEvidence(freeDevice.getId(), LocalDate.of(2026, 3, 1)))
                .extracting(MaintenanceCoverage::getClassification).contains(CoverageClassification.FREE);
        Equipment unknownDevice = equipment.findByEquipmentCode("DEMO-EQ-003").orElseThrow();
        assertThat(coverage.findEvidenceForEquipment(unknownDevice.getId()))
                .extracting(MaintenanceCoverage::getClassification).contains(CoverageClassification.NOT_FREE);
        assertThat(coverage.count()).isEqualTo(35);
    }

    @Test
    void pendingApprovalQueuesArePagedAndHistoricalRoundsRemain() {
        var planQueue = requests.findByStatusAndRequestType(ApprovalRequestStatus.PENDING,
                ApprovalRequestType.PLAN_APPROVAL, PageRequest.of(0, 2, REQUEST_ORDER));
        var vendorFirst = requests.findByStatusAndRequestType(ApprovalRequestStatus.PENDING,
                ApprovalRequestType.VENDOR_SELECTION, PageRequest.of(0, 1, REQUEST_ORDER));
        var vendorSecond = requests.findByStatusAndRequestType(ApprovalRequestStatus.PENDING,
                ApprovalRequestType.VENDOR_SELECTION, PageRequest.of(1, 1, REQUEST_ORDER));
        assertThat(planQueue.getTotalElements()).isEqualTo(1);
        assertThat(vendorFirst.getTotalElements()).isEqualTo(2);
        assertThat(vendorFirst.getContent().get(0).getId()).isNotEqualTo(vendorSecond.getContent().get(0).getId());
        assertThat(requests.findByStatus(ApprovalRequestStatus.PENDING,
                PageRequest.of(0, 2, REQUEST_ORDER)).getTotalElements()).isEqualTo(3);
        assertThat(vendorFirst.getContent().get(0).getCreatedByUser().getUsername()).isNotBlank();
        assertThat(vendorFirst.getContent().get(0).getProposedProvider().getName()).isNotBlank();

        Long revisionPlanId = jdbc.queryForObject("SELECT id FROM maintenance_plan WHERE title = ?", Long.class,
                "DEMO — Kế hoạch tháng 10/2026 đã duyệt");
        var rounds = requests.findByPlan_IdOrderBySubmittedAtAscIdAsc(revisionPlanId);
        assertThat(rounds).hasSize(2);
        assertThat(rounds).extracting(ApprovalRequest::getSubmittedAt).isSorted();
        assertThat(actions.findByRequest_Id(rounds.get(0).getId()).orElseThrow().getOutcome())
                .isEqualTo(ApprovalOutcome.REVISION_REQUIRED);
        assertThat(actions.findByRequest_Id(rounds.get(1).getId()).orElseThrow().getOutcome())
                .isEqualTo(ApprovalOutcome.APPROVE);
        assertThat(actions.count()).isEqualTo(15);

        Equipment pendingDevice = equipment.findByEquipmentCode("DEMO-EQ-034").orElseThrow();
        var pendingItem = items.findByEquipment_Id(pendingDevice.getId(),
                PageRequest.of(0, 1, ITEM_HISTORY_ORDER)).getContent().get(0);
        assertThat(requests.findByPlanItem_IdOrderBySubmittedAtAscIdAsc(pendingItem.getId()))
                .anySatisfy(request -> assertThat(request.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING));
    }

    @Test
    void executionsLogsAcceptancesReportsAndHistoryAreChronological() {
        Equipment device = equipment.findByEquipmentCode("DEMO-EQ-004").orElseThrow();
        var itemRows = items.findByEquipment_Id(device.getId(), PageRequest.of(0, 10, ITEM_HISTORY_ORDER));
        MaintenancePlanItem completed = itemRows.getContent().stream()
                .filter(i -> i.getStatus() == PlanItemStatus.COMPLETED).findFirst().orElseThrow();
        MaintenancePlanItem repair = itemRows.getContent().stream()
                .filter(i -> i.getStatus() == PlanItemStatus.REPAIR_REQUIRED).findFirst().orElseThrow();
        var attempts = executions.findByPlanItem_IdOrderByAttemptNoAsc(completed.getId());
        assertThat(attempts).extracting(MaintenanceExecution::getAttemptNo).containsExactly(1, 2);
        assertThat(executions.findFirstByPlanItem_IdOrderByAttemptNoDesc(completed.getId()))
                .contains(attempts.get(1));
        assertThat(executions.findByPlanItem_IdAndAttemptNo(completed.getId(), 1)).contains(attempts.get(0));
        var workLogs = logs.findByExecution_IdOrderByEventAtAscIdAsc(attempts.get(0).getId());
        assertThat(workLogs).isNotEmpty();
        assertThat(workLogs).extracting(MaintenanceProgressLog::getEventAt).isSorted();
        assertThat(acceptances.findByExecution_IdAndAcceptanceType(attempts.get(0).getId(),
                AcceptanceType.TECHNICAL_ACCEPTANCE)).get().extracting(AcceptanceRecord::getResult)
                .isEqualTo(AcceptanceResult.FAIL);
        assertThat(acceptances.findByExecution_IdOrderByObservedAtAscIdAsc(attempts.get(1).getId()))
                .extracting(AcceptanceRecord::getAcceptanceType)
                .containsExactly(AcceptanceType.TECHNICAL_ACCEPTANCE, AcceptanceType.HANDOVER_ACCEPTANCE);
        var itemHistory = history.findByPlanItem_IdOrderByActionTimestampAscIdAsc(repair.getId());
        assertThat(itemHistory).extracting(StatusHistory::getActionTimestamp).isSorted();
        assertThat(itemHistory.get(itemHistory.size() - 1).getNewState()).isEqualTo("REPAIR_REQUIRED");
        assertThat(itemHistory.get(itemHistory.size() - 1).getReason()).isNotBlank();
        assertThat(history.findByPlan_IdOrderByActionTimestampAscIdAsc(completed.getPlan().getId()))
                .extracting(StatusHistory::getActionTimestamp).isSorted();
        var actorHistory = history.findByActorUser_Id(itemHistory.get(0).getActorUser().getId(),
                PageRequest.of(0, 10, Sort.by("actionTimestamp").descending().and(Sort.by("id"))));
        assertThat(actorHistory.hasContent()).isTrue();
        assertThat(reports.findByPlan_Id(completed.getPlan().getId())).isPresent();
        assertThat(reports.findByStatus(ReportStatus.FINAL,
                PageRequest.of(0, 10, Sort.by("reportDate").descending().and(Sort.by("id"))))
                .getTotalElements()).isEqualTo(2);
    }

    @Test
    void ds02AndDs05KeepExternalApprovalAndFailedHandoverEvidence() {
        Equipment external = equipment.findByEquipmentCode("DEMO-EQ-010").orElseThrow();
        var externalItems = items.findByEquipment_Id(external.getId(), PageRequest.of(0, 10, ITEM_HISTORY_ORDER));
        MaintenancePlanItem july = externalItems.getContent().stream()
                .filter(i -> i.getPlan().getTitle().contains("07/2026")).findFirst().orElseThrow();
        assertThat(july.getAssignmentRoute()).isEqualTo(AssignmentRoute.EXTERNAL_APPROVED);
        assertThat(requests.findByPlanItem_IdOrderBySubmittedAtAscIdAsc(july.getId()))
                .anySatisfy(r -> assertThat(actions.findByRequest_Id(r.getId()).orElseThrow().getOutcome())
                        .isEqualTo(ApprovalOutcome.APPROVE));

        Equipment handoverDevice = equipment.findByEquipmentCode("DEMO-EQ-006").orElseThrow();
        MaintenancePlanItem handoverItem = items.findByEquipment_Id(handoverDevice.getId(),
                        PageRequest.of(0, 10, ITEM_HISTORY_ORDER)).getContent().stream()
                .filter(i -> i.getStatus() == PlanItemStatus.COMPLETED).findFirst().orElseThrow();
        var attempts = executions.findByPlanItem_IdOrderByAttemptNoAsc(handoverItem.getId());
        assertThat(attempts).hasSize(2);
        assertThat(acceptances.findByExecution_IdAndAcceptanceType(attempts.get(0).getId(),
                AcceptanceType.HANDOVER_ACCEPTANCE).orElseThrow().getResult()).isEqualTo(AcceptanceResult.FAIL);
        assertThat(acceptances.findByExecution_IdAndAcceptanceType(attempts.get(1).getId(),
                AcceptanceType.HANDOVER_ACCEPTANCE).orElseThrow().getResult()).isEqualTo(AcceptanceResult.PASS);
    }

    @Test
    void ds01Ds07AndDs08PreserveContractUnknownAndPendingEvidence() {
        Equipment freeDevice = equipment.findByEquipmentCode("DEMO-EQ-011").orElseThrow();
        MaintenancePlanItem freeItem = items.findByEquipment_Id(freeDevice.getId(),
                        PageRequest.of(0, 10, ITEM_HISTORY_ORDER)).getContent().stream()
                .filter(i -> i.getStatus() == PlanItemStatus.COMPLETED).findFirst().orElseThrow();
        assertThat(freeItem.getAssignmentRoute()).isEqualTo(AssignmentRoute.UNDER_CONTRACT);
        assertThat(freeItem.getCoverage().getClassification()).isEqualTo(CoverageClassification.FREE);
        assertThat(freeItem.getAssignedProvider().getId()).isEqualTo(freeItem.getCoverage().getProvider().getId());
        MaintenanceExecution freeAttempt = executions.findFirstByPlanItem_IdOrderByAttemptNoDesc(freeItem.getId())
                .orElseThrow();
        assertThat(freeAttempt.getProvider().getId()).isEqualTo(freeItem.getAssignedProvider().getId());
        assertThat(acceptances.findByExecution_IdAndAcceptanceType(freeAttempt.getId(),
                AcceptanceType.TECHNICAL_ACCEPTANCE).orElseThrow().getResult()).isEqualTo(AcceptanceResult.PASS);
        assertThat(acceptances.findByExecution_IdAndAcceptanceType(freeAttempt.getId(),
                AcceptanceType.HANDOVER_ACCEPTANCE).orElseThrow().getResult()).isEqualTo(AcceptanceResult.PASS);

        Equipment unknownDevice = equipment.findByEquipmentCode("DEMO-EQ-003").orElseThrow();
        MaintenancePlanItem unknownItem = items.findByEquipment_Id(unknownDevice.getId(),
                PageRequest.of(0, 1, ITEM_HISTORY_ORDER)).getContent().get(0);
        assertThat(unknownItem.getStatus()).isEqualTo(PlanItemStatus.PLANNED);
        assertThat(coverage.findEvidenceForEquipment(unknownDevice.getId()))
                .extracting(MaintenanceCoverage::getClassification).contains(CoverageClassification.NOT_FREE);
        assertThat(unknownItem.getAssignmentRoute()).isNull();
        assertThat(unknownItem.getAssignedProvider()).isNull();
        assertThat(executions.findByPlanItem_IdOrderByAttemptNoAsc(unknownItem.getId())).isEmpty();

        Equipment pendingDevice = equipment.findByEquipmentCode("DEMO-EQ-034").orElseThrow();
        MaintenancePlanItem pendingItem = items.findByEquipment_Id(pendingDevice.getId(),
                PageRequest.of(0, 1, ITEM_HISTORY_ORDER)).getContent().get(0);
        assertThat(pendingItem.getStatus()).isEqualTo(PlanItemStatus.WAITING_VENDOR_APPROVAL);
        assertThat(pendingItem.getAssignmentRoute()).isNull();
        assertThat(pendingItem.getAssignedProvider()).isNull();
        assertThat(executions.findByPlanItem_IdOrderByAttemptNoAsc(pendingItem.getId())).isEmpty();
        assertThat(requests.findByPlanItem_IdOrderBySubmittedAtAscIdAsc(pendingItem.getId()))
                .anySatisfy(r -> {
                    assertThat(r.getStatus()).isEqualTo(ApprovalRequestStatus.PENDING);
                    assertThat(r.getProposedProvider()).isNotNull();
                });
    }

    @Test
    void uc12FocusedRepositoryCallsAssembleTwoCampaignsWithoutAnApiDto() {
        int repositoryCalls = 0;
        Equipment device = equipment.findByEquipmentCode("DEMO-EQ-004").orElseThrow();
        repositoryCalls++;
        var campaigns = items.findByEquipment_Id(device.getId(), PageRequest.of(0, 10, ITEM_HISTORY_ORDER));
        repositoryCalls++;
        assertThat(campaigns.getTotalElements()).isEqualTo(2);
        Set<Long> planIds = new HashSet<>();
        List<MaintenanceExecution> allAttempts = new ArrayList<>();
        int noteCount = 0, acceptanceCount = 0, historyCount = 0;
        for (MaintenancePlanItem item : campaigns) {
            assertThat(Hibernate.isInitialized(item.getPlan())).isTrue();
            planIds.add(item.getPlan().getId());
            var itemAttempts = executions.findByPlanItem_IdOrderByAttemptNoAsc(item.getId());
            repositoryCalls++;
            allAttempts.addAll(itemAttempts);
            for (MaintenanceExecution attempt : itemAttempts) {
                noteCount += logs.findByExecution_IdOrderByEventAtAscIdAsc(attempt.getId()).size();
                repositoryCalls++;
                acceptanceCount += acceptances.findByExecution_IdOrderByObservedAtAscIdAsc(attempt.getId()).size();
                repositoryCalls++;
            }
            historyCount += history.findByPlanItem_IdOrderByActionTimestampAscIdAsc(item.getId()).size();
            repositoryCalls++;
        }
        assertThat(planIds).hasSize(2);
        assertThat(allAttempts).hasSize(3);
        assertThat(noteCount).isPositive();
        assertThat(acceptanceCount).isPositive();
        assertThat(historyCount).isPositive();
        assertThat(repositoryCalls).isEqualTo(12);
        assertThat(Set.of(campaigns.getContent().get(0).getStatus(), campaigns.getContent().get(1).getStatus()))
                .containsExactlyInAnyOrder(PlanItemStatus.COMPLETED, PlanItemStatus.REPAIR_REQUIRED);
    }
}
