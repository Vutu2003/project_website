package vn.edu.medmaintenance.persistence.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.MaintenancePlanItem;
import vn.edu.medmaintenance.persistence.enums.PlanItemStatus;

public interface MaintenancePlanItemRepository extends JpaRepository<MaintenancePlanItem, Long> {
    @EntityGraph(attributePaths = {"plan", "departmentAtPlan", "assignedProvider", "coverage"})
    @org.springframework.data.jpa.repository.Query("""
            select i from MaintenancePlanItem i join i.plan p
            where i.equipment.id = :equipmentId
            order by p.createdAt desc, p.id desc, i.id desc
            """)
    List<MaintenancePlanItem> findHistoryByEquipmentId(
            @org.springframework.data.repository.query.Param("equipmentId") Long equipmentId);
    List<MaintenancePlanItem> findAllByPlan_Id(Long planId);
    @EntityGraph(attributePaths = {"equipment", "departmentAtPlan", "assignedProvider"})
    Page<MaintenancePlanItem> findByPlan_IdAndDepartmentAtPlan_Id(
            Long planId, Long departmentId, Pageable pageable);
    @EntityGraph(attributePaths = {"equipment", "departmentAtPlan", "assignedProvider"})
    Page<MaintenancePlanItem> findByPlan_Id(Long planId, Pageable pageable);

    @EntityGraph(attributePaths = "equipment")
    Page<MaintenancePlanItem> findByPlan_IdAndStatus(Long planId, PlanItemStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"plan", "equipment"})
    Optional<MaintenancePlanItem> findByPlan_IdAndEquipment_Id(Long planId, Long equipmentId);

    @EntityGraph(attributePaths = {"plan", "equipment"})
    Page<MaintenancePlanItem> findByEquipment_Id(Long equipmentId, Pageable pageable);

    @EntityGraph(attributePaths = "equipment")
    Page<MaintenancePlanItem> findByStatus(PlanItemStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "equipment")
    Page<MaintenancePlanItem> findByDepartmentAtPlan_Id(Long departmentId, Pageable pageable);
}
