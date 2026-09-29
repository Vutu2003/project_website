package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "maintenance_plan_item")
public class MaintenancePlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MaintenancePlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id_at_plan", nullable = false)
    private Department departmentAtPlan;

    @Column(name = "planned_date", nullable = true)
    private LocalDate plannedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private PlanItemStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assigned_provider_id", nullable = true)
    private ServiceProvider assignedProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_route", nullable = true, columnDefinition = "text")
    private AssignmentRoute assignmentRoute;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "coverage_id", nullable = true)
    private MaintenanceCoverage coverage;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public MaintenancePlanItem() {
    }

    public Long getId() {
        return id;
    }

    public MaintenancePlan getPlan() {
        return plan;
    }

    public void setPlan(MaintenancePlan plan) {
        this.plan = plan;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public Department getDepartmentAtPlan() {
        return departmentAtPlan;
    }

    public void setDepartmentAtPlan(Department departmentAtPlan) {
        this.departmentAtPlan = departmentAtPlan;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public PlanItemStatus getStatus() {
        return status;
    }

    public void setStatus(PlanItemStatus status) {
        this.status = status;
    }

    public ServiceProvider getAssignedProvider() {
        return assignedProvider;
    }

    public void setAssignedProvider(ServiceProvider assignedProvider) {
        this.assignedProvider = assignedProvider;
    }

    public AssignmentRoute getAssignmentRoute() {
        return assignmentRoute;
    }

    public void setAssignmentRoute(AssignmentRoute assignmentRoute) {
        this.assignmentRoute = assignmentRoute;
    }

    public MaintenanceCoverage getCoverage() {
        return coverage;
    }

    public void setCoverage(MaintenanceCoverage coverage) {
        this.coverage = coverage;
    }

    public Integer getVersion() {
        return version;
    }

}
