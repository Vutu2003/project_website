package vn.edu.medmaintenance.api.dto.response;

import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.entity.UserNotification;

public record NotificationResponse(Long id, String notificationType, String title, String message, String targetUrl, OffsetDateTime createdAt, OffsetDateTime readAt) {
    public static NotificationResponse from(UserNotification n) {
        return new NotificationResponse(n.getId(), n.getNotificationType(), n.getTitle(), n.getMessage(), n.getTargetUrl(), n.getCreatedAt(), n.getReadAt());
    }
}
