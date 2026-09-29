package vn.edu.medmaintenance.security.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import vn.edu.medmaintenance.api.dto.response.ErrorResponse;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper objectMapper;
    public SecurityErrorWriter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(OffsetDateTime.now(ZoneOffset.UTC),
                status.value(), status.getReasonPhrase(), code, message, request.getRequestURI(), List.of()));
    }
}
