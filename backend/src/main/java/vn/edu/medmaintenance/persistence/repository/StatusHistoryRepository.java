package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.StatusHistory;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select h from StatusHistory h where h.planItem.id in :ids
            order by h.planItem.id, h.actionTimestamp, h.id
            """)
    List<StatusHistory> findHistoryByItemIds(
            @org.springframework.data.repository.query.Param("ids") Collection<Long> ids);

    @org.springframework.data.jpa.repository.Query("""
            select h from StatusHistory h where h.plan.id in :ids
            order by h.plan.id, h.actionTimestamp, h.id
            """)
    List<StatusHistory> findHistoryByPlanIds(
            @org.springframework.data.repository.query.Param("ids") Collection<Long> ids);
    List<StatusHistory> findByPlan_IdOrderByActionTimestampAscIdAsc(Long planId);
    List<StatusHistory> findByPlanItem_IdOrderByActionTimestampAscIdAsc(Long planItemId);
    Page<StatusHistory> findByActorUser_Id(Long actorId, Pageable pageable);
}
