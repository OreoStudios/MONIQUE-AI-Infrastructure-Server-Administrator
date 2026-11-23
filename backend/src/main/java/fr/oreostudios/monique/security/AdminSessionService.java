package fr.oreostudios.monique.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A browser session contains an opaque random ID in an HttpOnly cookie.
 * The permanent MONIQUE_ADMIN_TOKEN is never persisted in web storage.
 *
 * Sessions are intentionally in memory for the current single-instance preview:
 * restarting the backend revokes every session.
 */
@Service
public final class AdminSessionService {
    static final String COOKIE_NAME = "MONIQUE_SESSION";
    static final Duration LIFETIME = Duration.ofHours(12);
    private static final int MAX_SESSIONS = 32;

    private final byte[] adminToken;
    private final boolean secureCookie;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public record Session(String csrf, long expiresAt) {}
    public record CreatedSession(String id, String csrf) {}

    public AdminSessionService(
            @Value("${MONIQUE_ADMIN_TOKEN:}") String adminToken,
            @Value("${monique.web-origin}") String webOrigin) {
        if (adminToken == null || adminToken.length() < 32) {
            throw new IllegalStateException("MONIQUE_ADMIN_TOKEN must have at least 32 random characters");
        }
        this.adminToken = adminToken.getBytes(StandardCharsets.UTF_8);
        this.secureCookie = webOrigin.startsWith("https://");
    }

    public boolean matchesAdminToken(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                adminToken, candidate.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized CreatedSession create() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        // Bound memory and the number of simultaneously valid sessions.
        if (sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("Maximum active admin sessions reached; sign out another session");
        }
        String rawId = randomValue();
        String csrf = randomValue();
        sessions.put(hash(rawId), new Session(csrf, now + LIFETIME.toMillis()));
        return new CreatedSession(rawId, csrf);
    }

    public Session fromRequest(HttpServletRequest request) {
        String rawId = readCookie(request);
        if (rawId == null || rawId.length() > 128) return null;
        String id = hash(rawId);
        Session session = sessions.get(id);
        if (session == null) return null;
        if (session.expiresAt() <= System.currentTimeMillis()) {
            sessions.remove(id);
            return null;
        }
        return session;
    }

    public void revoke(HttpServletRequest request) {
        String rawId = readCookie(request);
        if (rawId != null && rawId.length() <= 128) {
            sessions.remove(hash(rawId));
        }
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

    private String hash(String input) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
