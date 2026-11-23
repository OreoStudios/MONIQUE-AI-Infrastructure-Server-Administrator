package fr.oreostudios.monique.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminSessionServiceTest {
    private final AdminSessionRepository repo = mock(AdminSessionRepository.class);
    private final String adminToken = "test-admin-secret-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private final AdminSessionService sessions =
            new AdminSessionService(repo, adminToken, "https://monique.oreostudios.fr");

    @Test
    void browserSessionUsesOpaqueSecureCookieAndRestoresFromDatabase() {
        when(repo.count()).thenReturn(0L);
        var issued = sessions.create();
        var response = new MockHttpServletResponse();
        sessions.setCookie(response, issued.id());
        String cookie = response.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Secure"));
        assertFalse(cookie.contains(adminToken));

        var captor = org.mockito.ArgumentCaptor.forClass(AdminSessionEntity.class);
        verify(repo).save(captor.capture());
        var stored = captor.getValue();
        assertNotEquals(stored.getIdHash(), issued.id());
        assertNotEquals(stored.getCsrfToken(), issued.id());
        when(repo.findById(stored.getIdHash())).thenReturn(Optional.of(stored));

        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AdminSessionService.COOKIE_NAME, issued.id()));
        var restored = sessions.fromRequest(request);
        assertNotNull(restored);
        assertTrue(AdminSessionService.validCsrf(restored, issued.session().csrf()));
        assertFalse(AdminSessionService.validCsrf(restored, "wrong-csrf"));
    }

    @Test
    void expiredOrRevokedSessionCannotBeRestored() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AdminSessionService.COOKIE_NAME, "an-expired-session"));
        when(repo.findById(any())).thenReturn(Optional.of(
                new AdminSessionEntity("x", "csrf", "fingerprint", Instant.now().minusSeconds(10))));
        assertNull(sessions.fromRequest(request));
    }

    @Test
    void wrongAdminTokenNeverCreatesSession() {
        assertFalse(sessions.matchesAdminToken("bad-token"));
        assertTrue(sessions.matchesAdminToken(adminToken));
        verifyNoInteractions(repo);
    }
}
