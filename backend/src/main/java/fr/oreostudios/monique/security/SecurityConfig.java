package fr.oreostudios.monique.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
           @Value("${MONIQUE_ADMIN_TOKEN:}") String token,
           @Value("${monique.web-origin}") String webOrigin,
           AdminSessionService sessions) throws Exception {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(webOrigin));
        cors.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-MONIQUE-CSRF"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);

        return http
            // We use a session-bound CSRF header for cookie-authenticated writes
            // (enforced in BearerAuthFilter) and a stateless SecurityContext.
            .csrf(csrf -> csrf.disable())
            .cors(c -> c.configurationSource(source))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    (request, response, error) -> response.sendError(401, "Authentication required")))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(new BearerAuthFilter(token, sessions), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
