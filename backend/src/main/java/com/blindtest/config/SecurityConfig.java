package com.blindtest.config;

import com.blindtest.error.RestSecurityErrorHandler;
import com.blindtest.security.JwtAuthFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Configures Spring Security: route access rules, CORS, CSRF, and JWT filter wiring. */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RestSecurityErrorHandler restSecurityErrorHandler;
    private final List<String> allowedOrigins;

    /** Constructs the security configuration with the JWT filter and allowed CORS origins. */
    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            RestSecurityErrorHandler restSecurityErrorHandler,
            @Value("${cors.allowed-origins:${app.base-url:http://localhost:4200}}")
                    String allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.restSecurityErrorHandler = restSecurityErrorHandler;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).
                map(String::trim).
                filter(origin -> !origin.isEmpty()).
                toList();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.cors(c -> c.configurationSource(corsConfigurationSource()));
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling(e -> e.
                authenticationEntryPoint(restSecurityErrorHandler).
                accessDeniedHandler(restSecurityErrorHandler));
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/error").permitAll();
            auth.requestMatchers("/actuator/health").permitAll();
            auth.requestMatchers("/actuator/metrics/**").hasRole("ADMIN");
            auth.requestMatchers("/api/auth/**").permitAll();
            auth.requestMatchers("/ws/**").permitAll();
            auth.requestMatchers(HttpMethod.GET, "/api/sessions/*/summary").authenticated();
            auth.requestMatchers(HttpMethod.GET, "/api/sessions/*", "/api/tracks",
                    "/api/playlists/**", "/media/**").permitAll();
            auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
            auth.anyRequest().authenticated();
        });
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
