package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "approval_request")
public class ApprovalRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, columnDefinition = "text")
    private ApprovalRequestType requestType;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "plan_id", nullable = true)
    private MaintenancePlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "plan_item_id", nullable = true)
    private MaintenancePlanItem planItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "proposed_provider_id", nullable = true)
    private ServiceProvider proposedProvider;

    @Column(name = "rationale", nullable = true, columnDefinition = "text")
    private String rationale;

    @Column(name = "warranty_impact_note", nullable = true, columnDefinition = "text")
    private String warrantyImpactNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private ApprovalRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserAccount createdByUser;

    @Column(name = "submitted_at", nullable = true)
    private OffsetDateTime submittedAt;

    @Column(name = "resolved_at", nullable = true)
    private OffsetDateTime resolvedAt;

    public ApprovalRequest() {
    }

    public Long getId() {
        return id;
    }

    public ApprovalRequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(ApprovalRequestType requestType) {
        this.requestType = requestType;
    }

    public MaintenancePlan getPlan() {
        return plan;
    }

    public void setPlan(MaintenancePlan plan) {
        this.plan = plan;
    }

    public MaintenancePlanItem getPlanItem() {
        return planItem;
    }

    public void setPlanItem(MaintenancePlanItem planItem) {
        this.planItem = planItem;
    }

    public ServiceProvider getProposedProvider() {
        return proposedProvider;
    }

    public void setProposedProvider(ServiceProvider proposedProvider) {
        this.proposedProvider = proposedProvider;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public String getWarrantyImpactNote() {
        return warrantyImpactNote;
    }

    public void setWarrantyImpactNote(String warrantyImpactNote) {
        this.warrantyImpactNote = warrantyImpactNote;
    }

    public ApprovalRequestStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalRequestStatus status) {
        this.status = status;
    }

    public UserAccount getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(UserAccount createdByUser) {
        this.createdByUser = createdByUser;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

}
