package fr.oreostudios.monique.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Session IDs are random and stored only as SHA-256 hashes in PostgreSQL.
 * The MONIQUE_ADMIN_TOKEN is checked only on initial login and is never
 * stored in cookies, browser storage or the session database.
 */
@Service
public class AdminSessionService {
    static final String COOKIE_NAME = "MONIQUE_SESSION";
    static final Duration LIFETIME = Duration.ofHours(12);
    private static final int MAX_SESSIONS = 32;

    private final AdminSessionRepository repository;
    private final byte[] adminToken;
    private final String adminFingerprint;
    private final boolean secureCookie;
    private final SecureRandom random = new SecureRandom();

    public record Session(String csrf, Instant expiresAt) {}
    public record CreatedSession(String id, Session session) {}

    public AdminSessionService(
            AdminSessionRepository repository,
            @Value("${MONIQUE_ADMIN_TOKEN:}") String adminToken,
            @Value("${monique.web-origin}") String webOrigin) {
        if (adminToken == null || adminToken.length() < 32) {
            throw new IllegalStateException("MONIQUE_ADMIN_TOKEN must contain 32+ random characters");
        }
        this.repository = repository;
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
        this.adminFingerprint = sha256(adminToken);
        this.secureCookie = webOrigin.startsWith("https://");
    }

    public boolean matchesAdminToken(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                adminToken, candidate.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public CreatedSession create() {
        repository.deleteExpired(Instant.now());
        if (repository.count() >= MAX_SESSIONS) {
            throw new IllegalStateException("Too many active administrator sessions");
        }
        String id = randomValue();
        String csrf = randomValue();
        Instant expiresAt = Instant.now().plus(LIFETIME);
        repository.save(new AdminSessionEntity(sha256(id), csrf, adminFingerprint, expiresAt));
        return new CreatedSession(id, new Session(csrf, expiresAt));
    }

    @Transactional(readOnly = true)
    public Session fromRequest(HttpServletRequest request) {
        String id = readCookie(request);
        if (id == null || id.length() > 128) return null;
        var entity = repository.findById(sha256(id)).orElse(null);
        if (entity == null || !entity.getExpiresAt().isAfter(Instant.now())
                || !MessageDigest.isEqual(entity.getAdminFingerprint().getBytes(StandardCharsets.UTF_8),
                    adminFingerprint.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return new Session(entity.getCsrfToken(), entity.getExpiresAt());
    }

    @Transactional
    public void revoke(HttpServletRequest request) {
        String id = readCookie(request);
        if (id != null && id.length() <= 128) repository.deleteById(sha256(id));
    }

    public static boolean validCsrf(Session session, String presented) {
        return session != null && presented != null
                && MessageDigest.isEqual(
                        session.csrf().getBytes(StandardCharsets.UTF_8),
                        presented.getBytes(StandardCharsets.UTF_8));
    }

    public void setCookie(HttpServletResponse response, String sessionId) {
        response.addHeader("Set-Cookie", COOKIE_NAME + "=" + sessionId
                + "; Path=/api; HttpOnly; SameSite=Strict; Max-Age=" + LIFETIME.toSeconds()
                + (secureCookie ? "; Secure" : ""));
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie", COOKIE_NAME
                + "=; Path=/api; HttpOnly; SameSite=Strict; Max-Age=0"
                + (secureCookie ? "; Secure" : ""));
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private String randomValue() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
