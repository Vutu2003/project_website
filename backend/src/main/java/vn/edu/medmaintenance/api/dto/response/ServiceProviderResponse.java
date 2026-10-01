package vn.edu.medmaintenance.api.dto.response;

public record ServiceProviderResponse(Long id, String code, String name, String contactDetails, boolean active) { }
