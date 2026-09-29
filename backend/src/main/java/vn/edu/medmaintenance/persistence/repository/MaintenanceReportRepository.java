package vn.edu.medmaintenance.persistence.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.MaintenanceReport;
import vn.edu.medmaintenance.persistence.enums.ReportStatus;

public interface MaintenanceReportRepository extends JpaRepository<MaintenanceReport, Long> {
    @org.springframework.data.jpa.repository.Query("select r from MaintenanceReport r where r.plan.id in :ids")
    List<MaintenanceReport> findHistoryByPlanIds(
            @org.springframework.data.repository.query.Param("ids") Collection<Long> ids);
    Optional<MaintenanceReport> findByPlan_Id(Long planId);
    Page<MaintenanceReport> findByStatus(ReportStatus status, Pageable pageable);
}
