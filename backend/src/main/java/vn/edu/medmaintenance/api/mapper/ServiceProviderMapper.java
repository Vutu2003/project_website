package vn.edu.medmaintenance.api.mapper;

import vn.edu.medmaintenance.api.dto.response.ServiceProviderResponse;
import vn.edu.medmaintenance.persistence.entity.ServiceProvider;

public final class ServiceProviderMapper {
    private ServiceProviderMapper() { }

    public static ServiceProviderResponse toResponse(ServiceProvider provider) {
        return new ServiceProviderResponse(provider.getId(), provider.getName(), provider.getActive());
    }
}
