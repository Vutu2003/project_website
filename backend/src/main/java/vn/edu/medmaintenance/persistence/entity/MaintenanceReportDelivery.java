package vn.edu.medmaintenance.persistence.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
@Entity @Table(name="maintenance_report_delivery")
public class MaintenanceReportDelivery {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id",nullable=false) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="report_id",nullable=false) private MaintenanceReport report;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="department_id") private Department department;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sent_by_user_id",nullable=false) private UserAccount sentBy;
 @Column(name="sent_at",nullable=false) private OffsetDateTime sentAt;
}
