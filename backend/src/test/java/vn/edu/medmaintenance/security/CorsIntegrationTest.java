package vn.edu.medmaintenance.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsIntegrationTest {
    @Autowired TestRestTemplate http;

    private ResponseEntity<String> preflight(String origin, String path, String method, String headers) {
        HttpHeaders request = new HttpHeaders();
        request.set("Origin", origin);
        request.set("Access-Control-Request-Method", method);
        request.set("Access-Control-Request-Headers", headers);
        return http.exchange(path, HttpMethod.OPTIONS, new HttpEntity<>(request), String.class);
    }

    @Test
    void exactFrontendOriginMayPreflightLoginAndAuthorizedRead() {
        var login = preflight("http://localhost:5173", "/api/auth/login", "POST", "content-type");
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(login.getHeaders().getAccessControlAllowCredentials()).isNotEqualTo(true);
        var read = preflight("http://localhost:5173", "/api/auth/me", "GET", "authorization");
        assertThat(read.getStatusCode().value()).isEqualTo(200);
        assertThat(read.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
    }

    @Test
    void unrelatedOriginIsNotAllowedAndAuthStillProtectsMe() {
        var blocked = preflight("http://unrelated.local:5173", "/api/auth/me", "GET", "authorization");
        assertThat(blocked.getHeaders().getAccessControlAllowOrigin()).isNull();
        assertThat(blocked.getStatusCode().is2xxSuccessful()).isFalse();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:5173");
        var anonymous = http.exchange("/api/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat(anonymous.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
    }
}
