package vn.edu.medmaintenance.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import org.springframework.data.domain.*;
import vn.edu.medmaintenance.persistence.entity.*;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;
import vn.edu.medmaintenance.api.dto.response.*;

@Service public class NotificationService {
    private final UserNotificationRepository notifications;
    private final UserAccountRepository users;
    private final CurrentUser current;
    public NotificationService(UserNotificationRepository notifications, UserAccountRepository users, CurrentUser current) {
        this.notifications=notifications;
        this.users=users;
        this.current=current;
    }
    @Transactional(propagation=Propagation.MANDATORY) public void notifyRole(UserRole role, Long departmentId, UserAccount actor, String type, String title, String message, String url) {
        if (role==UserRole.ADMIN) return;
        var at=OffsetDateTime.now(ZoneOffset.UTC);
        for (var user:users.findByRoleCodeOrderByUsernameAsc(role)) {
            if (!Boolean.TRUE.equals(user.getActive()) || (actor!=null && user.getId().equals(actor.getId())))continue;
            if (role==UserRole.KHOA_PHONG && (departmentId==null || user.getDepartment()==null || !departmentId.equals(user.getDepartment().getId())))continue;
            notifications.save(new UserNotification(user, type, title, message, url, at));
        }
    }
    @Transactional(readOnly=true) public PageResponse<NotificationResponse> list(Pageable page) {
        return PageResponse.from(notifications.findByUserAccount_Id(current.get().id(), PageRequest.of(page.getPageNumber(), page.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt", "id"))), NotificationResponse::from);
    }
    @Transactional(readOnly=true) public long unread() {
        return notifications.countByUserAccount_IdAndReadAtIsNull(current.get().id());
    }
    @Transactional public NotificationResponse read(Long id) {
        var n=notifications.findByIdAndUserAccount_Id(id, current.get().id()).orElseThrow(()->new BusinessRuleException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Không tìm thấy thông báo."));
        n.markRead(OffsetDateTime.now(ZoneOffset.UTC));
        return NotificationResponse.from(n);
    }
    @Transactional public void readAll() {
        notifications.markAllRead(current.get().id(), OffsetDateTime.now(ZoneOffset.UTC));
    }
}
