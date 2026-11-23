package fr.oreostudios.monique.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** Exchange the admin token for a durable, opaque HttpOnly browser session. */
@RestController
@RequestMapping("/api/auth")
public class AdminSessionController {
    private final AdminSessionService sessions;

    public AdminSessionController(AdminSessionService sessions) {
        this.sessions = sessions;
    }

    public record LoginRequest(@NotBlank String token) {}
    public record SessionResponse(boolean authenticated, String csrf, Instant expiresAt) {}

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        if (!sessions.matchesAdminToken(request.token())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
        AdminSessionService.CreatedSession created;
        try {
            created = sessions.create();
        } catch (IllegalStateException tooMany) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many active admin sessions. Sign out on another device.");
        }
        sessions.setCookie(response, created.id());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(asResponse(created.session()));
    }

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> current(HttpServletRequest request) {
        AdminSessionService.Session session = sessions.fromRequest(request);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrator session expired");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(asResponse(session));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessions.revoke(request);
        sessions.clearCookie(response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static SessionResponse asResponse(AdminSessionService.Session session) {
        return new SessionResponse(true, session.csrf(), session.expiresAt());
    }
}
