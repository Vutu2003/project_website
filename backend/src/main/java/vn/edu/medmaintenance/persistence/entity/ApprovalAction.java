package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "approval_action")
public class ApprovalAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ApprovalRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private UserAccount actorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, columnDefinition = "text")
    private ApprovalOutcome outcome;

    @Column(name = "comment", nullable = true, columnDefinition = "text")
    private String comment;

    @Column(name = "action_at", nullable = false)
    private OffsetDateTime actionAt;

    public ApprovalAction() {
    }

    public Long getId() {
        return id;
    }

    public ApprovalRequest getRequest() {
        return request;
    }

    public void setRequest(ApprovalRequest request) {
        this.request = request;
    }

    public UserAccount getActorUser() {
        return actorUser;
    }

    public void setActorUser(UserAccount actorUser) {
        this.actorUser = actorUser;
    }

    public ApprovalOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(ApprovalOutcome outcome) {
        this.outcome = outcome;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public OffsetDateTime getActionAt() {
        return actionAt;
    }

    public void setActionAt(OffsetDateTime actionAt) {
        this.actionAt = actionAt;
    }

}
