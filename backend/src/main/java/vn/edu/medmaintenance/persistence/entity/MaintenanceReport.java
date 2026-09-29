package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "maintenance_report")
public class MaintenanceReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MaintenancePlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private UserAccount createdByUser;

    @Column(name = "report_number", nullable = true, columnDefinition = "text")
    private String reportNumber;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Column(name = "work_done", nullable = true, columnDefinition = "text")
    private String workDone;

    @Column(name = "achieved", nullable = true, columnDefinition = "text")
    private String achieved;

    @Column(name = "not_achieved", nullable = true, columnDefinition = "text")
    private String notAchieved;

    @Column(name = "causes", nullable = true, columnDefinition = "text")
    private String causes;

    @Column(name = "next_work", nullable = true, columnDefinition = "text")
    private String nextWork;

    @Column(name = "resolutions", nullable = true, columnDefinition = "text")
    private String resolutions;

    @Column(name = "recommendations", nullable = true, columnDefinition = "text")
    private String recommendations;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    private ReportStatus status;

    @Column(name = "finalized_at", nullable = true)
    private OffsetDateTime finalizedAt;

    public MaintenanceReport() {
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

    public UserAccount getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(UserAccount createdByUser) {
        this.createdByUser = createdByUser;
    }

    public String getReportNumber() {
        return reportNumber;
    }

    public void setReportNumber(String reportNumber) {
        this.reportNumber = reportNumber;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public String getWorkDone() {
        return workDone;
    }

    public void setWorkDone(String workDone) {
        this.workDone = workDone;
    }

    public String getAchieved() {
        return achieved;
    }

    public void setAchieved(String achieved) {
        this.achieved = achieved;
    }

    public String getNotAchieved() {
        return notAchieved;
    }

    public void setNotAchieved(String notAchieved) {
        this.notAchieved = notAchieved;
    }

    public String getCauses() {
        return causes;
    }

    public void setCauses(String causes) {
        this.causes = causes;
    }

    public String getNextWork() {
        return nextWork;
    }

    public void setNextWork(String nextWork) {
        this.nextWork = nextWork;
    }

    public String getResolutions() {
        return resolutions;
    }

    public void setResolutions(String resolutions) {
        this.resolutions = resolutions;
    }

    public String getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(String recommendations) {
        this.recommendations = recommendations;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public OffsetDateTime getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(OffsetDateTime finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

}
