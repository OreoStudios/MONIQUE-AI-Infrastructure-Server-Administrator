package fr.oreostudios.monique.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Mints short-lived WebRTC credentials after normal MONIQUE bearer authentication.
 * The permanent OPENAI_API_KEY never leaves this Spring Boot process.
 *
 * This is a planning-only voice assistant: no function tools are exposed and
 * it has no route to execute commands against registered Pods.
 */
@RestController
@RequestMapping("/api/voice")
public final class VoiceSessionController {
    private static final String INSTRUCTIONS = """
        You are MONIQUE, the calm, professional female voice of OreoStudios' infrastructure assistant.
        Speak naturally with concise, practical answers. Reply in the user's language.
        You are a planning-only assistant: you have NO SSH, shell, server, database, or deployment tools.
        Never claim to have connected to, checked, modified, restarted, migrated, or secured a real server.
        Identify assumptions; for risky operations suggest backups, impact checks, and human approval.
        Never ask the user to say passwords, private keys, credentials or tokens aloud.
        Do not pretend the decorative avatar video is lip-synchronized to your speech.
        """;

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String voice;
    private final String safetyIdentifier;

    // Single-admin preview: limit ephemeral credential minting and API spend.
    private final ArrayDeque<Long> oneMinute = new ArrayDeque<>();
    private final ArrayDeque<Long> oneHour = new ArrayDeque<>();

    public VoiceSessionController(
            RestClient.Builder builder,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${monique.voice-model:gpt-realtime-2.1-mini}") String model,
            @Value("${monique.voice-name:marin}") String voice,
            @Value("${MONIQUE_ADMIN_TOKEN:}") String adminToken) {
        this.client = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
        this.safetyIdentifier = sha256(adminToken);
    }

    @PostMapping(path = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createSession() {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENAI_API_KEY is not configured");
        }
        enforceRateLimit();

        Map<String, Object> config = Map.of(
                "expires_after", Map.of("anchor", "created_at", "seconds", 60),
                "session", Map.of(
                        "type", "realtime",
                        "model", model,
                        "instructions", INSTRUCTIONS,
                        "output_modalities", List.of("audio"),
                        "max_output_tokens", 800,
                        "audio", Map.of(
                                "input", Map.of(
                                        "transcription", Map.of("model", "gpt-realtime-whisper"),
                                        "turn_detection", Map.of(
                                                "type", "semantic_vad",
                                                "eagerness", "auto",
                                                "create_response", true,
                                                "interrupt_response", true)),
                                "output", Map.of("voice", voice)))
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri("https://api.openai.com/v1/realtime/client_secrets")
                    .headers(headers -> {
                        headers.setBearerAuth(apiKey);
                        headers.set("OpenAI-Safety-Identifier", safetyIdentifier);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(config)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("value") instanceof String key) || key.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Could not create the realtime voice session");
            }
            // Return the ephemeral token only, never the upstream session metadata or permanent key.
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("value", key));
        } catch (RestClientResponseException exception) {
            // Do not echo upstream responses, which can contain project/account details.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "OpenAI voice session request failed (HTTP " + exception.getStatusCode().value() + ")");
        }
    }

    private synchronized void enforceRateLimit() {
        long now = System.currentTimeMillis();
        expire(oneMinute, now - Duration.ofMinutes(1).toMillis());
        expire(oneHour, now - Duration.ofHours(1).toMillis());
        if (oneMinute.size() >= 4 || oneHour.size() >= 20) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many voice sessions; please try again later");
        }
        oneMinute.addLast(now);
        oneHour.addLast(now);
    }

    private static void expire(ArrayDeque<Long> queue, long cutoff) {
        while (!queue.isEmpty() && queue.peekFirst() <= cutoff) {
            queue.removeFirst();
        }
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
