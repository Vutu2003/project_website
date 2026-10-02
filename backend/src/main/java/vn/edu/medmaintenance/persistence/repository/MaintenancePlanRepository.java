package vn.edu.medmaintenance.persistence.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlan;
import vn.edu.medmaintenance.persistence.enums.PlanStatus;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, Long> {
    @EntityGraph(attributePaths = "createdByUser")
    @Query("""
            select p from MaintenancePlan p where (:status is null or p.status = :status)
            and exists (select i.id from MaintenancePlanItem i where i.plan = p
                and i.status = :itemStatus
                and (:departmentId is null or i.departmentAtPlan.id = :departmentId))
            """)
    Page<MaintenancePlan> findWithItemsInStatus(@Param("departmentId") Long departmentId,
            @Param("status") PlanStatus status,
            @Param("itemStatus") vn.edu.medmaintenance.persistence.enums.PlanItemStatus itemStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = "createdByUser")
    @Query("""
            select p from MaintenancePlan p where exists (
                select i.id from MaintenancePlanItem i
                where i.plan = p and i.departmentAtPlan.id = :departmentId)
            and (:status is null or p.status = :status)
            """)
    Page<MaintenancePlan> findVisibleForDepartment(
            @Param("departmentId") Long departmentId,
            @Param("status") PlanStatus status, Pageable pageable);

    @Query("""
            select count(i) > 0 from MaintenancePlanItem i
            where i.plan.id = :planId and i.departmentAtPlan.id = :departmentId
            """)
    boolean visibleToDepartment(@Param("planId") Long planId,
            @Param("departmentId") Long departmentId);
    @EntityGraph(attributePaths = "createdByUser")
    Page<MaintenancePlan> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "createdByUser")
    Page<MaintenancePlan> findByStatus(PlanStatus status, Pageable pageable);

    @Query("select p from MaintenancePlan p join fetch p.createdByUser where p.id = :planId")
    Optional<MaintenancePlan> findWithCreatorById(@Param("planId") Long planId);
}
