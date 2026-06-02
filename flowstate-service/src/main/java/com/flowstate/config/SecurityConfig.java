package com.flowstate.config;

import com.flowstate.security.ApiKeyAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/**
 * Spring Security wiring — defines WHAT is protected and HOW auth failures respond.
 * Auth logic itself lives in com.flowstate.security.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public — no auth required
                .requestMatchers("/memory/health").permitAll()
                .requestMatchers("/v1/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers("/", "/*.html", "/*.css", "/*.js", "/*.ico",
                                    "/css/**", "/js/**").permitAll()

                // Everything else requires API key
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                // Always 200 — Zapier treats non-2xx as failure and retries
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"success\":false,\"message\":\"Unauthorized - Invalid or missing API key\"}"
                    );
                })
            );

        http.addFilterBefore(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class);
        return http.build();
    }
}
