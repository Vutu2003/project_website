package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, Long>, JpaSpecificationExecutor<ServiceProvider> {
    List<ServiceProvider> findByActiveTrueOrderByNameAsc();
    boolean existsByCode(String code);
    boolean existsByCodeAndIdNot(String code, Long id);
}
