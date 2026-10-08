package com.bingo.app.infrastructure.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final TokenAuthFilter tokenAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/**", "/api/auth/**", "/ws/**", "/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // The container HEALTHCHECK curls this with `curl -f`, which
                        // treats a 401 as a failure. Without this the app starts fine
                        // but is reported unhealthy, blocking anything that waits on
                        // service_healthy. Keep it limited to health, not all of actuator.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/screenshots/upload").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/games/active").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/games/{id}/register").hasRole("PLAYER")
                        .anyRequest().authenticated()
                )
                // Unauthenticated (401) / unauthorized (403) requests must still
                // carry the standard error envelope; the defaults reply with an
                // empty body the mobile client cannot interpret.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new SecurityErrorHandlers.JsonAuthenticationEntryPoint())
                        .accessDeniedHandler(new SecurityErrorHandlers.JsonAccessDeniedHandler()))
                .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}