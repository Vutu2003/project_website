package vn.edu.medmaintenance.api.mapper;

import vn.edu.medmaintenance.api.dto.response.DepartmentResponse;
import vn.edu.medmaintenance.persistence.entity.Department;

public final class DepartmentMapper {
    private DepartmentMapper() { }

    public static DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(department.getId(), department.getCode(),
                department.getName(), department.getActive());
    }
}
