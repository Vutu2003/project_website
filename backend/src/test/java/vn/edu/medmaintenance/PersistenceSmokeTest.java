package vn.edu.medmaintenance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;

@SpringBootTest
@Transactional
class PersistenceSmokeTest {
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void seededEnumsAndLazyRelationshipsCanBeLoaded() {
        Long equipmentId = jdbc.queryForObject("SELECT min(id) FROM equipment", Long.class);
        Equipment equipment = entityManager.find(Equipment.class, equipmentId);
        assertThat(equipment).isNotNull();
        assertThat(Hibernate.isInitialized(equipment.getDepartment())).isFalse();
        assertThat(equipment.getDepartment().getName()).isNotBlank();

        Long itemId = jdbc.queryForObject("SELECT min(id) FROM maintenance_plan_item", Long.class);
        MaintenancePlanItem item = entityManager.find(MaintenancePlanItem.class, itemId);
        assertThat(item.getPlan().getId()).isNotNull();
        assertThat(item.getEquipment().getEquipmentCode()).isNotBlank();
        String dbStatus = jdbc.queryForObject("SELECT status FROM maintenance_plan WHERE id = ?",
                String.class, item.getPlan().getId());
        assertThat(item.getPlan().getStatus()).isEqualTo(PlanStatus.valueOf(dbStatus));

        Long requestId = jdbc.queryForObject("SELECT min(id) FROM approval_request", Long.class);
        ApprovalRequest request = entityManager.find(ApprovalRequest.class, requestId);
        assertThat(request.getPlan() != null ^ request.getPlanItem() != null).isTrue();

        Long executionId = jdbc.queryForObject("SELECT min(id) FROM maintenance_execution", Long.class);
        MaintenanceExecution execution = entityManager.find(MaintenanceExecution.class, executionId);
        assertThat(execution.getPlanItem().getId()).isNotNull();
        assertThat(execution.getProvider().getName()).isNotBlank();

        Long historyId = jdbc.queryForObject("SELECT min(id) FROM status_history", Long.class);
        StatusHistory history = entityManager.find(StatusHistory.class, historyId);
        assertThat(history.getPlan() != null ^ history.getPlanItem() != null).isTrue();
    }

    @Test
    void versionIncrementsOnPlanAndItemUpdateThenTransactionRollsBack() {
        Long planId = jdbc.queryForObject("SELECT min(id) FROM maintenance_plan", Long.class);
        MaintenancePlan plan = entityManager.find(MaintenancePlan.class, planId);
        int planVersion = plan.getVersion();
        plan.setTitle(plan.getTitle() + " [rollback test]");
        entityManager.flush();
        assertThat(plan.getVersion()).isEqualTo(planVersion + 1);

        Long itemId = jdbc.queryForObject(
                "SELECT min(id) FROM maintenance_plan_item WHERE planned_date IS NOT NULL", Long.class);
        MaintenancePlanItem item = entityManager.find(MaintenancePlanItem.class, itemId);
        int itemVersion = item.getVersion();
        item.setPlannedDate(item.getPlannedDate().plusDays(1));
        entityManager.flush();
        assertThat(item.getVersion()).isEqualTo(itemVersion + 1);
    }
}
