package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.AcceptanceRecord;
import vn.edu.medmaintenance.persistence.enums.AcceptanceType;

public interface AcceptanceRecordRepository extends JpaRepository<AcceptanceRecord, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select a from AcceptanceRecord a where a.execution.id in :ids
            order by a.execution.id, a.observedAt, a.id
            """)
    List<AcceptanceRecord> findHistoryByExecutionIds(
            @org.springframework.data.repository.query.Param("ids") Collection<Long> ids);
    List<AcceptanceRecord> findByExecution_IdOrderByObservedAtAscIdAsc(Long executionId);
    Optional<AcceptanceRecord> findByExecution_IdAndAcceptanceType(Long executionId, AcceptanceType type);
}
