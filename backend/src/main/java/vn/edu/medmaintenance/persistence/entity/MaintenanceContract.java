package vn.edu.medmaintenance.persistence.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity @Table(name="maintenance_contract")
public class MaintenanceContract {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="id",nullable=false) private Long id;
 @Column(name="contract_code",columnDefinition="text",nullable=false) private String code;
 @Column(name="contract_name",columnDefinition="text",nullable=false) private String name;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="provider_id",nullable=false) private ServiceProvider provider;
 @Column(name="start_date",nullable=false) private LocalDate startDate;
 @Column(name="end_date",nullable=false) private LocalDate endDate;
 @Column(name="active",nullable=false) private Boolean active;
 @Column(name="notes",columnDefinition="text") private String notes;
 public Long getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
 public ServiceProvider getProvider(){return provider;} public LocalDate getStartDate(){return startDate;}
 public LocalDate getEndDate(){return endDate;} public Boolean getActive(){return active;}
}
