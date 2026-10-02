package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity @Table(name="user_notification") public class UserNotification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id", nullable=false) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_account_id", nullable=false) private UserAccount userAccount;
    @Column(name="notification_type", nullable=false, columnDefinition="text") private String notificationType;
    @Column(name="title", nullable=false, columnDefinition="text") private String title;
    @Column(name="message", nullable=false, columnDefinition="text") private String message;
    @Column(name="target_url", nullable=false, columnDefinition="text") private String targetUrl;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="read_at") private OffsetDateTime readAt;
    protected UserNotification() {
    }
    public UserNotification(UserAccount user, String type, String title, String message, String url, OffsetDateTime at) {
        this.userAccount=user;
        this.notificationType=type;
        this.title=title;
        this.message=message;
        this.targetUrl=url;
        this.createdAt=at;
    }
    public Long getId() {
        return id;
    }
    public UserAccount getUserAccount() {
        return userAccount;
    }
    public String getNotificationType() {
        return notificationType;
    }
    public String getTitle() {
        return title;
    }
    public String getMessage() {
        return message;
    }
    public String getTargetUrl() {
        return targetUrl;
    }
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
    public OffsetDateTime getReadAt() {
        return readAt;
    }
    public void markRead(OffsetDateTime at) {
        if (readAt==null)readAt=at;
    }
}
