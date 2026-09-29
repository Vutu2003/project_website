package vn.edu.medmaintenance.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.edu.medmaintenance.api.exception.ApiExceptionHandler;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void conflictAndUnexpectedErrorsHideTechnicalDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/equipment");
        var conflict = handler.conflict(new DataIntegrityViolationException("constraint marker"), request);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(conflict.getBody().code()).isEqualTo("DATA_CONFLICT");
        assertThat(conflict.getBody().message()).isEqualTo("Data conflict");
        assertThat(conflict.getBody().toString()).doesNotContain("constraint marker");

        var stale = handler.optimisticConflict(
                new ObjectOptimisticLockingFailureException("MaintenancePlan", 1L), request);
        assertThat(stale.getStatusCode().value()).isEqualTo(409);
        assertThat(stale.getBody().code()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT");
        assertThat(stale.getBody().toString()).doesNotContain("ObjectOptimisticLockingFailureException");

        var unexpected = handler.unexpected(new IllegalStateException("internal marker"), request);
        assertThat(unexpected.getStatusCode().value()).isEqualTo(500);
        assertThat(unexpected.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(unexpected.getBody().message()).isEqualTo("Unexpected server error");
        assertThat(unexpected.getBody().toString()).doesNotContain("internal marker");
    }
}
