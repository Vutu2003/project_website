package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "acceptance_record")
public class AcceptanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private MaintenanceExecution execution;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_type", nullable = false, columnDefinition = "text")
    private AcceptanceType acceptanceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, columnDefinition = "text")
    private AcceptanceResult result;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "conclusion", nullable = false, columnDefinition = "text")
    private String conclusion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private UserAccount recordedByUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "department_confirmed_by_user_id", nullable = true)
    private UserAccount departmentConfirmedByUser;

    @Column(name = "department_confirmed_at", nullable = true)
    private OffsetDateTime departmentConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "vtyt_confirmed_by_user_id", nullable = true)
    private UserAccount vtytConfirmedByUser;

    @Column(name = "vtyt_confirmed_at", nullable = true)
    private OffsetDateTime vtytConfirmedAt;

    public AcceptanceRecord() {
    }

    public Long getId() {
        return id;
    }

    public MaintenanceExecution getExecution() {
        return execution;
    }

    public void setExecution(MaintenanceExecution execution) {
        this.execution = execution;
    }

    public AcceptanceType getAcceptanceType() {
        return acceptanceType;
    }

    public void setAcceptanceType(AcceptanceType acceptanceType) {
        this.acceptanceType = acceptanceType;
    }

    public AcceptanceResult getResult() {
        return result;
    }

    public void setResult(AcceptanceResult result) {
        this.result = result;
    }

    public OffsetDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(OffsetDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public UserAccount getRecordedByUser() {
        return recordedByUser;
    }

    public void setRecordedByUser(UserAccount recordedByUser) {
        this.recordedByUser = recordedByUser;
    }

    public UserAccount getDepartmentConfirmedByUser() {
        return departmentConfirmedByUser;
    }

    public void setDepartmentConfirmedByUser(UserAccount departmentConfirmedByUser) {
        this.departmentConfirmedByUser = departmentConfirmedByUser;
    }

    public OffsetDateTime getDepartmentConfirmedAt() {
        return departmentConfirmedAt;
    }

    public void setDepartmentConfirmedAt(OffsetDateTime departmentConfirmedAt) {
        this.departmentConfirmedAt = departmentConfirmedAt;
    }

    public UserAccount getVtytConfirmedByUser() {
        return vtytConfirmedByUser;
    }

    public void setVtytConfirmedByUser(UserAccount vtytConfirmedByUser) {
        this.vtytConfirmedByUser = vtytConfirmedByUser;
    }

    public OffsetDateTime getVtytConfirmedAt() {
        return vtytConfirmedAt;
    }

    public void setVtytConfirmedAt(OffsetDateTime vtytConfirmedAt) {
        this.vtytConfirmedAt = vtytConfirmedAt;
    }

}
