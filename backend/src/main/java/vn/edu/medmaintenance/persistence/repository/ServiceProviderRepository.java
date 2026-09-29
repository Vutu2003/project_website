package vn.edu.medmaintenance.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, Long> {
    List<ServiceProvider> findByActiveTrueOrderByNameAsc();
}
