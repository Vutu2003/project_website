package vn.edu.medmaintenance.persistence.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.ApprovalAction;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {
    Optional<ApprovalAction> findByRequest_Id(Long requestId);
}
