package vn.edu.medmaintenance.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.edu.medmaintenance.security.exception.SecurityErrorWriter;
import vn.edu.medmaintenance.security.jwt.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-origin}") String frontendOrigin) {
        URI origin = URI.create(frontendOrigin);
        if (frontendOrigin.contains("*") || origin.getHost() == null
                || !("http".equals(origin.getScheme()) || "https".equals(origin.getScheme()))
                || (origin.getRawPath() != null && !origin.getRawPath().isEmpty())
                || origin.getRawQuery() != null || origin.getRawFragment() != null) {
            throw new IllegalArgumentException("FRONTEND_ORIGIN must be one explicit HTTP(S) origin");
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(frontendOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-VTYT-Authorization"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            SecurityErrorWriter errors) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/plans/*/report/finalize").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/plans/*/report").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.PUT, "/api/plans/*/report").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.GET, "/api/plans/*/report")
                            .hasAnyRole("PHONG_VTYT", "BAN_GIAM_DOC")
                        .requestMatchers(HttpMethod.GET, "/api/equipment/*/maintenance-history")
                            .hasAnyRole("PHONG_VTYT", "BAN_GIAM_DOC", "KHOA_PHONG")
                        .requestMatchers(HttpMethod.GET, "/api/equipment/*/coverages")
                            .hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.GET, "/api/approvals/*")
                            .hasRole("BAN_GIAM_DOC")
                        .requestMatchers(HttpMethod.GET, "/api/plan-items/*/vendor-proposals/draft")
                            .hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/plans").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.PATCH, "/api/plans/*").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/plans/*/submit").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/plan-items/*/route").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/plan-items/*/vendor-proposals").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/plan-items/*/executions").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/executions/*/progress").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/executions/*/complete-work").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/executions/*/repair-required").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/executions/*/technical-acceptance").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/executions/*/handover").hasRole("KHOA_PHONG")
                        .requestMatchers(HttpMethod.POST, "/api/vendor-proposals/*/submit").hasRole("PHONG_VTYT")
                        .requestMatchers(HttpMethod.POST, "/api/approvals/*/decision").hasRole("BAN_GIAM_DOC")
                        .requestMatchers(HttpMethod.GET, "/api/approvals/pending")
                            .hasRole("BAN_GIAM_DOC")
                        .requestMatchers("/api/admin/accounts", "/api/admin/accounts/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, failure) -> errors.write(request, response,
                                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication required"))
                        .accessDeniedHandler((request, response, failure) -> errors.write(request, response,
                                HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
