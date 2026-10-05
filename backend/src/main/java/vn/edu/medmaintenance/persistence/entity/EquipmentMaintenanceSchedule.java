package vn.edu.medmaintenance.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="equipment_maintenance_schedule")
public class EquipmentMaintenanceSchedule {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id",nullable=false) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="equipment_id",nullable=false) private Equipment equipment;
 @Column(name="quarter",nullable=false,columnDefinition="text") private String quarter;
 public Long getId(){return id;}
 public Equipment getEquipment(){return equipment;}
 public String getQuarter(){return quarter;}
}
