package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "maintenance_execution")
public class MaintenanceExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_item_id", nullable = false)
    private MaintenancePlanItem planItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private ServiceProvider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "started_by_user_id", nullable = false)
    private UserAccount startedByUser;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at", nullable = true)
    private OffsetDateTime endedAt;

    @Column(name = "result_note", nullable = true, columnDefinition = "text")
    private String resultNote;

    public MaintenanceExecution() {
    }

    public Long getId() {
        return id;
    }

    public MaintenancePlanItem getPlanItem() {
        return planItem;
    }

    public void setPlanItem(MaintenancePlanItem planItem) {
        this.planItem = planItem;
    }

    public ServiceProvider getProvider() {
        return provider;
    }

    public void setProvider(ServiceProvider provider) {
        this.provider = provider;
    }

    public UserAccount getStartedByUser() {
        return startedByUser;
    }

    public void setStartedByUser(UserAccount startedByUser) {
        this.startedByUser = startedByUser;
    }

    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getResultNote() {
        return resultNote;
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote;
    }

}
