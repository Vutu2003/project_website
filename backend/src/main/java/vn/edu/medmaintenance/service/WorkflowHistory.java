package vn.edu.medmaintenance.service;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.entity.StatusHistory;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.repository.StatusHistoryRepository;

@Component
class WorkflowHistory {
    private final StatusHistoryRepository histories;

    WorkflowHistory(StatusHistoryRepository histories) { this.histories = histories; }

    void plan(MaintenancePlan plan, UserAccount actor, String oldState, String newState,
            String action, String reason, OffsetDateTime at) {
        StatusHistory history = new StatusHistory();
        history.setPlan(plan);
        fill(history, actor, oldState, newState, action, reason, at);
        histories.save(history);
    }

    void item(MaintenancePlanItem item, UserAccount actor, String action, OffsetDateTime at) {
        StatusHistory history = new StatusHistory();
        history.setPlanItem(item);
        fill(history, actor, null, "PLANNED", action, null, at);
        histories.save(history);
    }

    void itemTransition(MaintenancePlanItem item, UserAccount actor, String oldState,
            String newState, String action, String reason, OffsetDateTime at) {
        StatusHistory history = new StatusHistory();
        history.setPlanItem(item);
        fill(history, actor, oldState, newState, action, reason, at);
        histories.save(history);
    }

    private void fill(StatusHistory history, UserAccount actor, String oldState, String newState,
            String action, String reason, OffsetDateTime at) {
        history.setActorUser(actor);
        history.setOldState(oldState);
        history.setNewState(newState);
        history.setAction(action);
        history.setReason(reason);
        history.setActionTimestamp(at);
    }
}
