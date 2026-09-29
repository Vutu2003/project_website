package vn.edu.medmaintenance.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.edu.medmaintenance.api.dto.response.ErrorResponse;
import vn.edu.medmaintenance.api.dto.response.FieldErrorResponse;
import vn.edu.medmaintenance.security.auth.InvalidCredentialsException;
import vn.edu.medmaintenance.service.BusinessRuleException;
import org.springframework.dao.OptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> validation(BindException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fields = exception.getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(),
                        error.isBindingFailure() ? "invalid value" : error.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> constraints(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldErrorResponse> fields = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString().replaceAll("^.*\\.", ""),
                        violation.getMessage())).toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> methodValidation(HandlerMethodValidationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, List.of());
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ErrorResponse> invalidParameter(InvalidParameterException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", exception.getMessage(), request,
                List.of(new FieldErrorResponse(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidInput(Exception exception, HttpServletRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : exception instanceof MissingServletRequestParameterException missing
                ? missing.getParameterName() : "request";
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid request parameter", request,
                List.of(new FieldErrorResponse(field, "invalid value")));
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> notFound(Exception exception, HttpServletRequest request) {
        String message = exception instanceof ResourceNotFoundException ? exception.getMessage() : "Resource not found";
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message, request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", request, List.of());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException exception,
            HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials", request, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> business(BusinessRuleException exception,
            HttpServletRequest request) {
        return error(exception.status(), exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> optimisticConflict(Exception exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT",
                "Plan has changed; reload before retrying", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> conflict(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        LOG.warn("Data conflict at {}", request.getRequestURI(), exception);
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "Data conflict", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        LOG.error("Unexpected API failure at {}", request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Unexpected server error", request, List.of());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request, List<FieldErrorResponse> fields) {
        return ResponseEntity.status(status).body(new ErrorResponse(OffsetDateTime.now(ZoneOffset.UTC),
                status.value(), status.getReasonPhrase(), code, message, request.getRequestURI(), fields));
    }
}
