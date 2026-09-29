package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import vn.edu.medmaintenance.persistence.entity.UserAccount;
import vn.edu.medmaintenance.persistence.enums.UserRole;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<UserAccount> {
    @Override
    @EntityGraph(attributePaths = "department")
    org.springframework.data.domain.Page<UserAccount> findAll(
            org.springframework.data.jpa.domain.Specification<UserAccount> specification,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findForUpdateById(@org.springframework.data.repository.query.Param("id") Long id);

    @EntityGraph(attributePaths = "department")
    Optional<UserAccount> findByUsername(String username);
    @EntityGraph(attributePaths = "department")
    Optional<UserAccount> findWithDepartmentById(Long id);
    List<UserAccount> findByRoleCodeOrderByUsernameAsc(UserRole roleCode);
    List<UserAccount> findByDepartment_IdOrderByUsernameAsc(Long departmentId);
}
