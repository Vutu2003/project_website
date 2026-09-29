package vn.edu.medmaintenance.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.medmaintenance.persistence.entity.MaintenanceCoverage;

public interface MaintenanceCoverageRepository extends JpaRepository<MaintenanceCoverage, Long> {
    @EntityGraph(attributePaths = {"provider", "verifiedByUser"})
    @Query("""
            select c from MaintenanceCoverage c
            where c.equipment.id = :equipmentId
            order by case when c.effectiveFrom is null then 1 else 0 end,
                     c.effectiveFrom desc, c.id desc
            """)
    List<MaintenanceCoverage> findDetailedEvidenceForEquipment(@Param("equipmentId") Long equipmentId);

    @Query("""
            select c from MaintenanceCoverage c
            where c.equipment.id = :equipmentId
            order by case when c.effectiveFrom is null then 1 else 0 end,
                     c.effectiveFrom desc, c.id desc
            """)
    List<MaintenanceCoverage> findEvidenceForEquipment(@Param("equipmentId") Long equipmentId);

    @Query("""
            select c from MaintenanceCoverage c
            where c.equipment.id = :equipmentId
              and (c.effectiveFrom is null or c.effectiveFrom <= :onDate)
              and (c.effectiveTo is null or c.effectiveTo >= :onDate)
            order by case when c.effectiveFrom is null then 1 else 0 end,
                     c.effectiveFrom desc, c.id desc
            """)
    List<MaintenanceCoverage> findDateApplicableEvidence(
            @Param("equipmentId") Long equipmentId, @Param("onDate") LocalDate onDate);
}
