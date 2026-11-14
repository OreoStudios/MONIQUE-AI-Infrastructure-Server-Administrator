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

public final class BearerAuthFilter extends OncePerRequestFilter {
    private final byte[] expected;

    public BearerAuthFilter(String token) {
        if (token == null || token.length() < 32) {
            throw new IllegalStateException("MONIQUE_ADMIN_TOKEN must be configured with 32+ random characters");
        }
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            byte[] presented = header.substring(7).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, presented)) {
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                        "monique-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            }
        }
        chain.doFilter(request, response);
    }
}
