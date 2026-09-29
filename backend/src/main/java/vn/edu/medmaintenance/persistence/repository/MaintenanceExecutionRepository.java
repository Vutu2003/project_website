package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.MaintenanceExecution;

public interface MaintenanceExecutionRepository extends JpaRepository<MaintenanceExecution, Long> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "provider")
    @org.springframework.data.jpa.repository.Query("""
            select e from MaintenanceExecution e where e.planItem.id in :ids
            order by e.planItem.id, e.attemptNo, e.id
            """)
    List<MaintenanceExecution> findHistoryByItemIds(
            @org.springframework.data.repository.query.Param("ids") Collection<Long> ids);
    List<MaintenanceExecution> findByPlanItem_IdOrderByAttemptNoAsc(Long planItemId);
    Optional<MaintenanceExecution> findFirstByPlanItem_IdOrderByAttemptNoDesc(Long planItemId);
    Optional<MaintenanceExecution> findByPlanItem_IdAndAttemptNo(Long planItemId, Integer attemptNo);
}
