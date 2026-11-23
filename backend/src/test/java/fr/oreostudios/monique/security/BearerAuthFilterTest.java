package fr.oreostudios.monique.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class BearerAuthFilterTest {
    private static final String TOKEN = "some-test-admin-token-that-is-at-least-32-characters";

    @Test
    void rejectsCookieAuthenticatedWriteWithoutCsrf() throws Exception {
        var sessions = mock(AdminSessionService.class);
        when(sessions.fromRequest(any())).thenReturn(
                new AdminSessionService.Session("valid-csrf", Instant.now().plusSeconds(3600)));
        var request = new MockHttpServletRequest("POST", "/api/ai/chat");
        request.setCookies(new Cookie("MONIQUE_SESSION", "opaque"));
        var response = new MockHttpServletResponse();
        new BearerAuthFilter(TOKEN, sessions).doFilter(request, response, new MockFilterChain());
        assertEquals(403, response.getStatus());
    }

    @Test
    void browserCookieWorksWhenReverseProxyAlsoSendsBasicAuthorization() throws Exception {
        var sessions = mock(AdminSessionService.class);
        when(sessions.fromRequest(any())).thenReturn(
                new AdminSessionService.Session("valid-csrf", Instant.now().plusSeconds(3600)));
        var request = new MockHttpServletRequest("POST", "/api/ai/chat");
        request.addHeader("Authorization", "Basic unrelated-proxy-credentials");
        request.addHeader("X-MONIQUE-CSRF", "valid-csrf");
        request.setCookies(new Cookie("MONIQUE_SESSION", "opaque"));
        var response = new MockHttpServletResponse();
        new BearerAuthFilter(TOKEN, sessions).doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
        verify(sessions).fromRequest(any());
    }

    @Test
    void bearerClientCanStillMakeAuthenticatedApiCalls() throws Exception {
        var sessions = mock(AdminSessionService.class);
        var request = new MockHttpServletRequest("POST", "/api/ai/chat");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        var response = new MockHttpServletResponse();
        new BearerAuthFilter(TOKEN, sessions).doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
        verifyNoInteractions(sessions);
    }
}
