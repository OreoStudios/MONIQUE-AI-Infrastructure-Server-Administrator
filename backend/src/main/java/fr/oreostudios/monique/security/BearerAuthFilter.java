package fr.oreostudios.monique.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Supports legacy Bearer API clients as well as the new HttpOnly session cookie.
 * Cookie-authenticated writes require a session-bound CSRF token; Bearer clients
 * do not send ambient credentials and therefore do not need cookie CSRF checking.
 */
public final class BearerAuthFilter extends OncePerRequestFilter {
    private final byte[] expected;
    private final AdminSessionService sessions;

    public BearerAuthFilter(String token, AdminSessionService sessions) {
        if (token == null || token.length() < 32) {
            throw new IllegalStateException("MONIQUE_ADMIN_TOKEN must be configured with 32+ random characters");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Login is an explicit token exchange; a stale ambient cookie must never
        // prevent re-authentication.
        if (request.getServletPath().equals("/api/auth/login")) {
            chain.doFilter(request, response);
            return;
        }

        boolean bearerAuthenticated = false;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            byte[] presented = header.substring(7).getBytes(StandardCharsets.UTF_8);
            bearerAuthenticated = MessageDigest.isEqual(expected, presented);
        }
        if (bearerAuthenticated) {
            authenticate();
        } else if (header == null || !header.startsWith("Bearer ")) {
            var session = sessions.fromRequest(request);
            if (session != null) {
                boolean readOnly = request.getMethod().equals("GET")
                        || request.getMethod().equals("HEAD")
                        || request.getMethod().equals("OPTIONS");
                if (!readOnly && !AdminSessionService.validCsrf(
                        session, request.getHeader("X-MONIQUE-CSRF"))) {
                    // Do not redispatch into a protected /error handler: that can
                    // turn a CSRF 403 into a misleading 401 ("session expired").
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setHeader("Cache-Control", "no-store");
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"csrf_validation_failed\"}");
                    return;
                }
                authenticate();
            }
        }
        chain.doFilter(request, response);
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "monique-admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
}
