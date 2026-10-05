package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "equipment")
public class Equipment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "equipment_code", nullable = false, columnDefinition = "text")
    private String equipmentCode;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "serial_number", nullable = true, columnDefinition = "text")
    private String serialNumber;

    @Column(name = "model", nullable = true, columnDefinition = "text")
    private String model;

    @Column(name = "technical_spec", nullable = true, columnDefinition = "text")
    private String technicalSpec;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manufacturer_provider_id")
    private ServiceProvider manufacturerProvider;

    public ServiceProvider getManufacturerProvider() { return manufacturerProvider; }
    public void setManufacturerProvider(ServiceProvider provider) { manufacturerProvider = provider; }

    @Column(name="maintenance_enabled",nullable=false) private Boolean maintenanceEnabled;
    @Column(name="maintenance_interval_value") private Integer maintenanceIntervalValue;
    @Column(name="maintenance_interval_unit",columnDefinition="text") private String maintenanceIntervalUnit;
    @Column(name="commissioning_date") private LocalDate commissioningDate;
    public Boolean getMaintenanceEnabled(){return maintenanceEnabled;}
    public Integer getMaintenanceIntervalValue(){return maintenanceIntervalValue;}
    public String getMaintenanceIntervalUnit(){return maintenanceIntervalUnit;}
    public LocalDate getCommissioningDate(){return commissioningDate;}
    protected Equipment() {
    }

    public Long getId() {
        return id;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getEquipmentCode() {
        return equipmentCode;
    }

    public void setEquipmentCode(String equipmentCode) {
        this.equipmentCode = equipmentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getTechnicalSpec() {
        return technicalSpec;
    }

    public void setTechnicalSpec(String technicalSpec) {
        this.technicalSpec = technicalSpec;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

}
