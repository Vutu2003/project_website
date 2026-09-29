package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "maintenance_progress_log")
public class MaintenanceProgressLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private MaintenanceExecution execution;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private UserAccount recordedByUser;

    @Column(name = "event_at", nullable = false)
    private OffsetDateTime eventAt;

    @Column(name = "work_note", nullable = false, columnDefinition = "text")
    private String workNote;

    @Column(name = "damage_note", nullable = true, columnDefinition = "text")
    private String damageNote;

    public MaintenanceProgressLog() {
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

    public UserAccount getRecordedByUser() {
        return recordedByUser;
    }

    public void setRecordedByUser(UserAccount recordedByUser) {
        this.recordedByUser = recordedByUser;
    }

    public OffsetDateTime getEventAt() {
        return eventAt;
    }

    public void setEventAt(OffsetDateTime eventAt) {
        this.eventAt = eventAt;
    }

    public String getWorkNote() {
        return workNote;
    }

    public void setWorkNote(String workNote) {
        this.workNote = workNote;
    }

    public String getDamageNote() {
        return damageNote;
    }

    public void setDamageNote(String damageNote) {
        this.damageNote = damageNote;
    }

}
