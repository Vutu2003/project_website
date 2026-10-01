package vn.edu.medmaintenance.service;

import java.util.Locale;
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
import vn.edu.medmaintenance.api.dto.response.DepartmentResponse;
import vn.edu.medmaintenance.api.dto.response.PageResponse;
import vn.edu.medmaintenance.api.mapper.DepartmentMapper;
import vn.edu.medmaintenance.persistence.entity.Department;
import vn.edu.medmaintenance.persistence.repository.DepartmentRepository;

@Service
@Transactional(readOnly = true)
public class AdminDepartmentService {
    private final DepartmentRepository departments;
    public AdminDepartmentService(DepartmentRepository departments) { this.departments = departments; }

    public PageResponse<DepartmentResponse> list(PageQuery page, String search, Boolean active) {
        String term = CatalogRules.search(search);
        String pattern = CatalogRules.pattern(term);
        Specification<Department> filter = (root, query, cb) -> cb.and(
                term.isEmpty() ? cb.conjunction() : cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')),
                active == null ? cb.conjunction() : cb.equal(root.get("active"), active));
        return PageResponse.from(departments.findAll(filter, PageRequests.create(page,
                Set.of("id", "code", "name", "active"), "code", Sort.Direction.ASC)),
                DepartmentMapper::toResponse);
    }

    public DepartmentResponse detail(Long id) { return DepartmentMapper.toResponse(find(id)); }

    @Transactional
    public DepartmentResponse create(CatalogRequest request) {
        String code = CatalogRules.required(request.code(), "INVALID_DEPARTMENT", "Mã khoa/phòng");
        String name = CatalogRules.required(request.name(), "INVALID_DEPARTMENT", "Tên khoa/phòng");
        if (departments.findByCode(code).isPresent()) duplicate();
        Department department = new Department();
        department.setCode(code); department.setName(name); department.setActive(true);
        save(department);
        return DepartmentMapper.toResponse(department);
    }

    @Transactional
    public DepartmentResponse edit(Long id, CatalogRequest request) {
        Department department = find(id);
        String code = CatalogRules.required(request.code(), "INVALID_DEPARTMENT", "Mã khoa/phòng");
        String name = CatalogRules.required(request.name(), "INVALID_DEPARTMENT", "Tên khoa/phòng");
        if (departments.existsByCodeAndIdNot(code, id)) duplicate();
        department.setCode(code); department.setName(name);
        save(department);
        return DepartmentMapper.toResponse(department);
    }

    @Transactional
    public DepartmentResponse activate(Long id) { return setActive(id, true); }
    @Transactional
    public DepartmentResponse deactivate(Long id) { return setActive(id, false); }
    private DepartmentResponse setActive(Long id, boolean active) {
        Department department = find(id);
        department.setActive(active); departments.flush();
        return DepartmentMapper.toResponse(department);
    }
    private Department find(Long id) {
        PageRequests.requirePositive(id, "id");
        return departments.findById(id).orElseThrow(() -> new BusinessRuleException(
                HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", "Không tìm thấy khoa/phòng."));
    }
    private void save(Department department) {
        try { departments.saveAndFlush(department); }
        catch (DataIntegrityViolationException failure) {
            if (String.valueOf(failure.getMostSpecificCause().getMessage()).contains("uq_department_code")) duplicate();
            throw failure;
        }
    }
    private void duplicate() { throw new BusinessRuleException(HttpStatus.CONFLICT,
            "DEPARTMENT_CODE_ALREADY_EXISTS", "Mã khoa/phòng đã tồn tại."); }
}
