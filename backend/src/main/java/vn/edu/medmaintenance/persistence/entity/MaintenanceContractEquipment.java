package vn.edu.medmaintenance.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="maintenance_contract_equipment")
public class MaintenanceContractEquipment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id",nullable=false) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="equipment_id",nullable=false) private Equipment equipment;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="contract_id",nullable=false) private MaintenanceContract contract;
 public Long getId(){return id;}
 public Equipment getEquipment(){return equipment;}
 public MaintenanceContract getContract(){return contract;}
}
