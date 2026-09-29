package vn.edu.medmaintenance.api.dto.response;

public record EquipmentResponse(
        Long id, String equipmentCode, String name, String model, String serialNumber,
        boolean active, Long departmentId, String departmentCode, String departmentName) { }
