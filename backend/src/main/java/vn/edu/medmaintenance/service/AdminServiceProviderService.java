package vn.edu.medmaintenance.service;

import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.common.PageRequests;
import vn.edu.medmaintenance.api.dto.request.CatalogRequest;
import vn.edu.medmaintenance.api.dto.request.PageQuery;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.api.dto.response.ServiceProviderResponse;
import vn.edu.medmaintenance.api.mapper.ServiceProviderMapper;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;
import vn.edu.medmaintenance.persistence.repository.ServiceProviderRepository;

@Service
@Transactional(readOnly = true)
public class AdminServiceProviderService {
    private final ServiceProviderRepository providers;
    public AdminServiceProviderService(ServiceProviderRepository providers) { this.providers = providers; }

    public PageResponse<ServiceProviderResponse> list(PageQuery page, String search, Boolean active) {
        String term = CatalogRules.search(search);
        String pattern = CatalogRules.pattern(term);
        Specification<ServiceProvider> filter = (root, query, cb) -> cb.and(
                term.isEmpty() ? cb.conjunction() : cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')),
                active == null ? cb.conjunction() : cb.equal(root.get("active"), active));
        return PageResponse.from(providers.findAll(filter, PageRequests.create(page,
                Set.of("id", "code", "name", "active"), "code", Sort.Direction.ASC)),
                ServiceProviderMapper::toResponse);
    }
    public ServiceProviderResponse detail(Long id) { return ServiceProviderMapper.toResponse(find(id)); }

    @Transactional
    public ServiceProviderResponse create(CatalogRequest request) {
        String code = CatalogRules.required(request.code(), "INVALID_SERVICE_PROVIDER", "Mã đơn vị");
        String name = CatalogRules.required(request.name(), "INVALID_SERVICE_PROVIDER", "Tên đơn vị");
        if (providers.existsByCode(code)) duplicate();
        ServiceProvider provider = new ServiceProvider();
        provider.setCode(code); provider.setName(name);
        provider.setContactDetails(CatalogRules.optional(request.contactDetails()));
        provider.setActive(true); save(provider);
        return ServiceProviderMapper.toResponse(provider);
    }
    @Transactional
    public ServiceProviderResponse edit(Long id, CatalogRequest request) {
        ServiceProvider provider = find(id);
        String code = CatalogRules.required(request.code(), "INVALID_SERVICE_PROVIDER", "Mã đơn vị");
        String name = CatalogRules.required(request.name(), "INVALID_SERVICE_PROVIDER", "Tên đơn vị");
        if (providers.existsByCodeAndIdNot(code, id)) duplicate();
        provider.setCode(code); provider.setName(name);
        provider.setContactDetails(CatalogRules.optional(request.contactDetails()));
        save(provider);
        return ServiceProviderMapper.toResponse(provider);
    }
    @Transactional
    public ServiceProviderResponse activate(Long id) { return setActive(id, true); }
    @Transactional
    public ServiceProviderResponse deactivate(Long id) { return setActive(id, false); }
    private ServiceProviderResponse setActive(Long id, boolean active) {
        ServiceProvider provider = find(id);
        provider.setActive(active); providers.flush();
        return ServiceProviderMapper.toResponse(provider);
    }
    private ServiceProvider find(Long id) {
        PageRequests.requirePositive(id, "id");
        return providers.findById(id).orElseThrow(() -> new BusinessRuleException(
                HttpStatus.NOT_FOUND, "SERVICE_PROVIDER_NOT_FOUND", "Không tìm thấy đơn vị bảo trì."));
    }
    private void save(ServiceProvider provider) {
        try { providers.saveAndFlush(provider); }
        catch (DataIntegrityViolationException failure) {
            if (String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uq_service_provider_code")) duplicate();
            throw failure;
        }
    }
    private void duplicate() { throw new BusinessRuleException(HttpStatus.CONFLICT,
            "SERVICE_PROVIDER_CODE_ALREADY_EXISTS", "Mã đơn vị bảo trì đã tồn tại."); }
}
