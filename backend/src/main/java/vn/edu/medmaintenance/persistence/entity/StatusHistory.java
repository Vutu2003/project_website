package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "status_history")
public class StatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "plan_id", nullable = true)
    private MaintenancePlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "plan_item_id", nullable = true)
    private MaintenancePlanItem planItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private UserAccount actorUser;

    @Column(name = "old_state", nullable = true, columnDefinition = "text")
    private String oldState;

    @Column(name = "new_state", nullable = false, columnDefinition = "text")
    private String newState;

    @Column(name = "action", nullable = false, columnDefinition = "text")
    private String action;

    @Column(name = "reason", nullable = true, columnDefinition = "text")
    private String reason;

    @Column(name = "action_timestamp", nullable = false)
    private OffsetDateTime actionTimestamp;

    public StatusHistory() {
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

    public MaintenancePlanItem getPlanItem() {
        return planItem;
    }

    public void setPlanItem(MaintenancePlanItem planItem) {
        this.planItem = planItem;
    }

    public UserAccount getActorUser() {
        return actorUser;
    }

    public void setActorUser(UserAccount actorUser) {
        this.actorUser = actorUser;
    }

    public String getOldState() {
        return oldState;
    }

    public void setOldState(String oldState) {
        this.oldState = oldState;
    }

    public String getNewState() {
        return newState;
    }

    public void setNewState(String newState) {
        this.newState = newState;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getActionTimestamp() {
        return actionTimestamp;
    }

    public void setActionTimestamp(OffsetDateTime actionTimestamp) {
        this.actionTimestamp = actionTimestamp;
    }

}
