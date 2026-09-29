package vn.edu.medmaintenance.api.mapper;

import vn.edu.medmaintenance.api.dto.response.EquipmentResponse;
import vn.edu.medmaintenance.persistence.entity.Department;
import vn.edu.medmaintenance.persistence.entity.Equipment;

public final class EquipmentMapper {
    private EquipmentMapper() { }

    public static EquipmentResponse toDepartmentResponse(Equipment equipment, Long departmentId) {
        if (equipment.getDepartment().getId().equals(departmentId)) return toResponse(equipment);
        return new EquipmentResponse(equipment.getId(), equipment.getEquipmentCode(),
                equipment.getName(), equipment.getModel(), equipment.getSerialNumber(),
                equipment.getActive(), null, null, null);
    }

    public static EquipmentResponse toResponse(Equipment equipment) {
        Department department = equipment.getDepartment();
        return new EquipmentResponse(equipment.getId(), equipment.getEquipmentCode(),
                equipment.getName(), equipment.getModel(), equipment.getSerialNumber(),
                equipment.getActive(), department.getId(), department.getCode(), department.getName());
    }
}
