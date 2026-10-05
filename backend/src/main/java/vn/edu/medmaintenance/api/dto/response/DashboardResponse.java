package vn.edu.medmaintenance.api.dto.response;

import java.time.*;
import java.util.List;
import vn.edu.medmaintenance.persistence.enums.UserRole;

/** Read projections only; no persistence entities or account credentials. */
public sealed interface DashboardResponse {
    UserRole role();
    Instant generatedAt();

    record Action(String label, String href) {}
    record Attention(String title, String detail, String status, Instant at, String href, String cta) {}
    record PlanRow(long id, String title, String status, int year, String quarter, long equipmentCount) {}
    record ItemRow(long itemId, long planId, long equipmentId, String equipmentCode, String equipmentName,
                   String provider, String status, Instant at, String note, String actor) {}
    record ReportRow(long planId, String title, int year, String quarter, LocalDate reportDate,
                     Instant at, long completed, long repair, String status) {}
    record ContractRow(long id, String code, String provider, LocalDate endDate, long equipmentCount) {}
    record ContractSummary(long valid, long expiring, long expired) {}
    record Progress(long notStarted, long inProgress, long workDone, long damaged, long technical, long handover) {}
    record Quarter(int year, String quarter, long scheduled, long underContract, long outsideContract,
                   long inProgress, long completed, PlanRow plan) {}
    record VtytSummary(long scheduled, long activeContracts, long maintaining, long technical, long awaitingReport) {}
    record Vtyt(UserRole role, Instant generatedAt, VtytSummary summary, List<Attention> attention,
                Quarter currentQuarter, Progress progress, ContractSummary contracts,
                List<ContractRow> expiringContracts, List<ItemRow> recentProgress,
                List<ReportRow> reports, List<Action> quickActions) implements DashboardResponse {}
    record ApprovalRow(long id, String type, String title, String equipmentCode, String sender, Instant at) {}
    record DecisionRow(long requestId, String title, String equipmentCode, String outcome,
                       String comment, String actor, Instant at) {}
    record BgdSummary(long pendingPlans, long pendingProviders, long approvedThisMonth,
                      long revisedThisMonth, long reportsThisMonth) {}
    record Bgd(UserRole role, Instant generatedAt, BgdSummary summary, List<ApprovalRow> pending,
               List<DecisionRow> decisions, List<ReportRow> reports, List<Action> quickActions) implements DashboardResponse {}
    record KhoaSummary(long equipment, long maintaining, long handover, long completedRecently) {}
    record Khoa(UserRole role, Instant generatedAt, long departmentId, String departmentName,
                KhoaSummary summary, List<ItemRow> handover, List<ItemRow> active,
                List<ItemRow> history, List<Action> quickActions) implements DashboardResponse {}
    record AdminSummary(long activeAccounts, long inactiveAccounts, long departments, long providers, long contracts) {}
    record RoleCount(UserRole role, long count) {}
    record Quality(String label, long count, String href) {}
    record Admin(UserRole role, Instant generatedAt, AdminSummary summary, List<RoleCount> accountsByRole,
                 List<Quality> quality, List<Action> quickActions) implements DashboardResponse {}
}
