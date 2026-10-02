package vn.edu.medmaintenance.persistence.repository;

import java.util.Optional;
import java.time.OffsetDateTime;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.medmaintenance.persistence.entity.UserNotification;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    Page<UserNotification> findByUserAccount_Id(Long userId, Pageable pageable);
    long countByUserAccount_IdAndReadAtIsNull(Long userId);
    Optional<UserNotification> findByIdAndUserAccount_Id(Long id, Long userId);
    @Modifying @Query("update UserNotification n set n.readAt=:at where n.userAccount.id=:userId and n.readAt is null") int markAllRead(@Param("userId") Long userId, @Param("at") OffsetDateTime at);
}
