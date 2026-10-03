package vn.edu.medmaintenance.service;

import java.time.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.medmaintenance.api.dto.request.UpdateWarrantyRequest;
import vn.edu.medmaintenance.api.dto.response.WarrantyResponse;
import vn.edu.medmaintenance.api.exception.ResourceNotFoundException;
import vn.edu.medmaintenance.persistence.entity.Equipment;
import vn.edu.medmaintenance.persistence.enums.UserRole;
import vn.edu.medmaintenance.persistence.repository.*;
import vn.edu.medmaintenance.security.principal.CurrentUser;

@Service
public class WarrantyService {
    private final EquipmentRepository equipment;
    private final MaintenanceCoverageRepository coverages;
    private final ServiceProviderRepository providers;
    private final CurrentUser current;
    private final EntityManager em;
    public WarrantyService(EquipmentRepository e, MaintenanceCoverageRepository c,
            ServiceProviderRepository p, CurrentUser u, EntityManager em) {
        equipment=e; coverages=c; providers=p; current=u; this.em=em;
    }
    private Equipment visible(Long id) {
        var device=equipment.findWithDepartmentById(id).orElseThrow(()->new ResourceNotFoundException("Equipment"));
        var actor=current.get();
        if (actor.role()==UserRole.KHOA_PHONG && !device.getDepartment().getId().equals(actor.departmentId()))
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "DEPARTMENT_SCOPE_VIOLATION", "Thiết bị không thuộc khoa của bạn.");
        return device;
    }
    @Transactional(readOnly=true)
    public WarrantyResponse detail(Long id, LocalDate referenceDate) {
        var device=visible(id);
        LocalDate date=referenceDate==null?LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")):referenceDate;
        var manufacturer=device.getManufacturerProvider();
        var contracts=coverages.findDetailedEvidenceForEquipment(id).stream()
            .map(c->new WarrantyResponse.Contract(c.getId(), c.getContractReference(),
                c.getProvider()==null?null:c.getProvider().getName(), c.getProvider()==null?null:c.getProvider().getContactDetails(),
                c.getCoverageScope(), c.getEffectiveFrom(), c.getEffectiveTo(), c.getWarrantyExpiresOn(),
                WarrantyStatus.at(c, date), c.getBasisNote())).toList();
        return new WarrantyResponse(id, device.getEquipmentCode(), device.getName(),
            manufacturer==null?null:manufacturer.getId(), manufacturer==null?null:manufacturer.getName(),
            manufacturer==null?null:manufacturer.getContactDetails(), manufacturer==null?null:manufacturer.getActive(), date, contracts);
    }
    @Transactional
    public WarrantyResponse update(Long id, UpdateWarrantyRequest input) {
        var role=current.get().role();
        if (role!=UserRole.PHONG_VTYT && role!=UserRole.ADMIN)
            throw new BusinessRuleException(HttpStatus.FORBIDDEN, "BUSINESS_ACCESS_DENIED", "Chỉ VTYT và ADMIN được cập nhật hồ sơ bảo hành.");
        var device=visible(id);
        em.lock(device, LockModeType.PESSIMISTIC_WRITE);
        var manufacturer=input.manufacturerProviderId()==null?null:providers.findById(input.manufacturerProviderId())
            .orElseThrow(()->new ResourceNotFoundException("Service provider"));
        if (manufacturer!=null && !Boolean.TRUE.equals(manufacturer.getActive()))
            throw new BusinessRuleException(HttpStatus.CONFLICT, "PROVIDER_INACTIVE", "Nhà sản xuất đã ngừng hoạt động.");
        device.setManufacturerProvider(manufacturer);
        var seen=new java.util.HashSet<Long>();
        for (var value:input.contracts()) {
            var coverage=coverages.findById(value.id()).orElseThrow(()->new ResourceNotFoundException("Coverage"));
            if (!coverage.getEquipment().getId().equals(id) || !seen.add(value.id()))
                throw new BusinessRuleException(HttpStatus.CONFLICT, "INVALID_WARRANTY_COVERAGE", "Hồ sơ hợp đồng không hợp lệ cho thiết bị.");
            em.lock(coverage, LockModeType.PESSIMISTIC_WRITE);
            if (value.warrantyExpiresOn()!=null && coverage.getEffectiveFrom()!=null && value.warrantyExpiresOn().isBefore(coverage.getEffectiveFrom()))
                throw new BusinessRuleException(HttpStatus.CONFLICT, "INVALID_WARRANTY_DATE", "Ngày hết bảo hành phải từ ngày bắt đầu hợp đồng trở đi.");
            coverage.setWarrantyExpiresOn(value.warrantyExpiresOn());
        }
        em.flush();
        return detail(id, null);
    }
}
