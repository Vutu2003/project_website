package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.MaintenanceProgressLog;

public interface MaintenanceProgressLogRepository extends JpaRepository<MaintenanceProgressLog, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select l from MaintenanceProgressLog l where l.execution.id in :ids
            order by l.execution.id, l.eventAt, l.id
            """)
    List<MaintenanceProgressLog> findHistoryByExecutionIds(
            @org.springframework.data.repository.query.Param("ids") Collection<Long> ids);
    List<MaintenanceProgressLog> findByExecution_IdOrderByEventAtAscIdAsc(Long executionId);
}
