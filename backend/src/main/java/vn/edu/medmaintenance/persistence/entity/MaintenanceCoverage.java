package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "maintenance_coverage")
public class MaintenanceCoverage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "provider_id", nullable = true)
    private ServiceProvider provider;

    @Column(name = "contract_reference", nullable = true, columnDefinition = "text")
    private String contractReference;

    @Column(name = "coverage_scope", nullable = true, columnDefinition = "text")
    private String coverageScope;

    @Column(name = "effective_from", nullable = true)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = true)
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", nullable = false, columnDefinition = "text")
    private CoverageClassification classification;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "verified_by_user_id", nullable = true)
    private UserAccount verifiedByUser;

    @Column(name = "verified_at", nullable = true)
    private OffsetDateTime verifiedAt;

    @Column(name = "basis_note", nullable = true, columnDefinition = "text")
    private String basisNote;

    protected MaintenanceCoverage() {
    }

    public Long getId() {
        return id;
    }

    public Equipment getEquipment() {
        return equipment;
    }

    public void setEquipment(Equipment equipment) {
        this.equipment = equipment;
    }

    public ServiceProvider getProvider() {
        return provider;
    }

    public void setProvider(ServiceProvider provider) {
        this.provider = provider;
    }

    public String getContractReference() {
        return contractReference;
    }

    public void setContractReference(String contractReference) {
        this.contractReference = contractReference;
    }

    public String getCoverageScope() {
        return coverageScope;
    }

    public void setCoverageScope(String coverageScope) {
        this.coverageScope = coverageScope;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public CoverageClassification getClassification() {
        return classification;
    }

    public void setClassification(CoverageClassification classification) {
        this.classification = classification;
    }

    public UserAccount getVerifiedByUser() {
        return verifiedByUser;
    }

    public void setVerifiedByUser(UserAccount verifiedByUser) {
        this.verifiedByUser = verifiedByUser;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(OffsetDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public String getBasisNote() {
        return basisNote;
    }

    public void setBasisNote(String basisNote) {
        this.basisNote = basisNote;
    }

}
