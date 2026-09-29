package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.ApprovalRequest;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestStatus;
import vn.edu.medmaintenance.persistence.enums.ApprovalRequestType;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    @EntityGraph(attributePaths = {"createdByUser", "proposedProvider", "plan", "planItem",
            "planItem.plan", "planItem.equipment", "planItem.coverage"})
    Optional<ApprovalRequest> findReviewById(Long id);

    @EntityGraph(attributePaths = {"proposedProvider", "planItem"})
    Optional<ApprovalRequest> findByPlanItem_IdAndRequestTypeAndStatus(
            Long itemId, ApprovalRequestType type, ApprovalRequestStatus status);

    boolean existsByPlanItem_IdAndRequestTypeAndStatus(
            Long itemId, ApprovalRequestType type, ApprovalRequestStatus status);
    boolean existsByPlan_IdAndRequestTypeAndStatus(Long planId, ApprovalRequestType type, ApprovalRequestStatus status);
    @EntityGraph(attributePaths = {"createdByUser", "proposedProvider", "plan", "planItem", "planItem.equipment"})
    Page<ApprovalRequest> findByStatus(ApprovalRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"createdByUser", "proposedProvider", "plan", "planItem", "planItem.equipment"})
    Page<ApprovalRequest> findByStatusAndRequestType(
            ApprovalRequestStatus status, ApprovalRequestType requestType, Pageable pageable);

    List<ApprovalRequest> findByPlan_IdOrderBySubmittedAtAscIdAsc(Long planId);
    List<ApprovalRequest> findByPlanItem_IdOrderBySubmittedAtAscIdAsc(Long planItemId);
}
