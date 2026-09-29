package vn.edu.medmaintenance.persistence.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import vn.edu.medmaintenance.persistence.enums.*;

@Entity
@Table(name = "user_account")
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "department_id", nullable = true)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, columnDefinition = "text")
    private UserRole roleCode;

    @Column(name = "username", nullable = false, columnDefinition = "text")
    private String username;

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;

    @Column(name = "active", nullable = false)
    private Boolean active;

    protected UserAccount() {
    }

    public static UserAccount createLoginAccount(String username, String passwordHash,
            UserRole role, Department department, boolean active) {
        var account = new UserAccount();
        account.setUsername(username); account.setDisplayName(username);
        account.setPasswordHash(passwordHash); account.setRoleCode(role);
        account.setDepartment(department); account.setActive(active);
        return account;
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

    public UserRole getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(UserRole roleCode) {
        this.roleCode = roleCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

}
