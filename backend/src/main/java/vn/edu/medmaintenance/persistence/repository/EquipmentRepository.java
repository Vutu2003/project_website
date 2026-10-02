package vn.edu.medmaintenance.persistence.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.Equipment;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    @EntityGraph(attributePaths = "department")
    @org.springframework.data.jpa.repository.Query("""
            select e from Equipment e where
            (:departmentId is null or e.department.id = :departmentId or (:historicalScope = true and exists (
                select i.id from MaintenancePlanItem i
                where i.equipment = e and i.departmentAtPlan.id = :departmentId)))
            and (:active is null or e.active = :active)
            and (lower(e.equipmentCode) like :pattern escape '!'
                or lower(e.name) like :pattern escape '!'
                or lower(e.serialNumber) like :pattern escape '!')
            """)
    Page<Equipment> searchVisible(
            @org.springframework.data.repository.query.Param("departmentId") Long departmentId,
            @org.springframework.data.repository.query.Param("active") Boolean active,
            @org.springframework.data.repository.query.Param("historicalScope") boolean historicalScope,
            @org.springframework.data.repository.query.Param("pattern") String pattern, Pageable pageable);

    @EntityGraph(attributePaths = "department")
    @org.springframework.data.jpa.repository.Query("""
            select e from Equipment e where
            (e.department.id = :departmentId or exists (
                select i.id from MaintenancePlanItem i
                where i.equipment = e and i.departmentAtPlan.id = :departmentId))
            and (:active is null or e.active = :active)
            """)
    Page<Equipment> findVisibleForDepartment(
            @org.springframework.data.repository.query.Param("departmentId") Long departmentId,
            @org.springframework.data.repository.query.Param("active") Boolean active,
            Pageable pageable);

    @EntityGraph(attributePaths = "department")
    @org.springframework.data.jpa.repository.Query("""
            select e from Equipment e where e.id = :id and
            (e.department.id = :departmentId or exists (
                select i.id from MaintenancePlanItem i
                where i.equipment = e and i.departmentAtPlan.id = :departmentId))
            """)
    Optional<Equipment> findVisibleForDepartmentById(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("departmentId") Long departmentId);
    @EntityGraph(attributePaths = "department")
    Optional<Equipment> findByEquipmentCode(String equipmentCode);

    @EntityGraph(attributePaths = "department")
    Page<Equipment> findByDepartment_Id(Long departmentId, Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Page<Equipment> findByActiveTrue(Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Page<Equipment> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Page<Equipment> findByActive(Boolean active, Pageable pageable);

    @EntityGraph(attributePaths = "department")
    Page<Equipment> findByDepartment_IdAndActive(Long departmentId, Boolean active, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
            "select e from Equipment e join fetch e.department where e.id = :id")
    Optional<Equipment> findWithDepartmentById(@org.springframework.data.repository.query.Param("id") Long id);
}
