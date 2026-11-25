package fr.oreostudios.monique.ai;

import fr.oreostudios.monique.execution.CommandService;
import fr.oreostudios.monique.pods.PodModels.PodView;
import fr.oreostudios.monique.pods.PodService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import java.util.UUID;

/**
 * Mints short-lived WebRTC credentials after normal MONIQUE bearer authentication.
 * The permanent OPENAI_API_KEY never leaves this Spring Boot process.
 *
 * When MONIQUE_EXECUTION_ENABLED is on and exactly one pod is selected, the voice
 * session is granted propose_command and approve_command function tools so spoken
 * requests can create and approve SSH jobs. Execution still runs through
 * CommandService: every job is stored immutably and the pod's SSH host fingerprint
 * must be pinned first. Without execution or a selected pod it stays planning-only.
 */
@RestController
@RequestMapping("/api/voice")
public final class VoiceSessionController {
    private static final String BASE_INSTRUCTIONS = """
        You are MONIQUE, the calm, professional female voice of OreoStudios' infrastructure assistant.
        Speak naturally with concise, practical answers. Reply in the user's language.
        Identify assumptions; for risky operations suggest backups, impact checks, and human approval.
        Never ask the user to say passwords, private keys, credentials or tokens aloud.
        Do not pretend the decorative avatar video is lip-synchronized to your speech.
        Ignore non-speech sounds like chairs, clicks, breathing and background noise. Do not answer unintelligible noise.
        """;

    private static final String PLANNING_INSTRUCTIONS = """
        You are a planning-only assistant: you have NO SSH, shell, server, database, or deployment tools.
        Never claim to have connected to, checked, modified, restarted, migrated, or secured a real server.
        You may discuss backend job status supplied by the app, but you cannot execute commands through voice.
        A running command is not a completed installation. Direct operations to text chat and the approval console.
        """;

    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final String voice;
    private final String safetyIdentifier;
    private final AssistantPreferences preferences;
    private final CommandService commands;
    private final PodService pods;

    public record SessionRequest(UUID podId) { }

    private String executionInstructions(PodView pod) {
        return """
            The operator has granted you STANDING AUTHORIZATION on the selected pod. You OPERATE it by voice
            with run_command(command, purpose, timeout_seconds), which executes ONE noninteractive SSH command
            immediately on the real server and returns its exit code and output. Do NOT ask for permission or
            confirmation before running; selecting the pod is the authorization. Say briefly and out loud what
            you are about to run, then run it. Execute ONE step at a time and wait for the returned result
            before the next step. Begin a connection or audit with non-mutating inspection, then proceed to the
            requested change. Report the real outcome honestly: never invent output, a zero exit code is not
            proof of success, and read back a concise summary of what the command returned. Put impact, risks,
            rollback and verification in the purpose field. Never read secrets, keys, tokens, private keys or
            environment variable values aloud. If a result says the SSH host fingerprint is not pinned, tell the
            operator to pin it once in the console; that host-identity check is the one step that cannot be
            skipped. If a command fails or the outcome is unknown, stop and report it rather than guessing.
            """ + "\nSelected pod (saved configuration, not live evidence): \"" + pod.name() + "\" reachable at "
            + pod.sshUsername() + "@" + pod.sshHost() + ":" + pod.sshPort()
            + "; credentialStored=" + pod.hasCredential() + ". Never target any other host.";
    }

    private static Map<String, Object> runSchema() {
        return Map.of("type", "object", "properties", Map.of(
            "command", Map.of("type", "string", "description", "Exact noninteractive shell command, maximum 12000 characters"),
            "purpose", Map.of("type", "string", "description", "Purpose, risks, rollback and verification; maximum 2000 characters"),
            "timeout_seconds", Map.of("type", "integer", "minimum", 10, "maximum", 1800)),
            "required", List.of("command", "purpose", "timeout_seconds"), "additionalProperties", false);
    }

    // Single-admin preview: limit ephemeral credential minting and API spend.
    private final ArrayDeque<Long> oneMinute = new ArrayDeque<>();
    private final ArrayDeque<Long> oneHour = new ArrayDeque<>();

    public VoiceSessionController(
            RestClient.Builder builder,
            @Value("${OPENAI_API_KEY:}") String apiKey,
            @Value("${monique.voice-model:gpt-realtime-2.1-mini}") String model,
            @Value("${monique.voice-name:marin}") String voice,
            @Value("${MONIQUE_ADMIN_TOKEN:}") String adminToken,
            AssistantPreferences preferences,
            CommandService commands,
            PodService pods) {
        this.client = builder.build();
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
        this.safetyIdentifier = sha256(adminToken);
        this.preferences = preferences;
        this.commands = commands;
        this.pods = pods;
    }

    @PostMapping(path = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createSession(@RequestBody(required = false) SessionRequest body) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENAI_API_KEY is not configured");
        }
        enforceRateLimit();
        var prefs = preferences.get();

        // Voice execution is only offered when the engine is enabled and exactly one
        // existing pod is selected. find() throws 404 for an unknown or stale id.
        UUID podId = body == null ? null : body.podId();
        PodView pod = commands.enabled() && podId != null ? pods.find(podId) : null;
        boolean execute = pod != null;
        String operation = execute ? executionInstructions(pod) : PLANNING_INSTRUCTIONS;

        var input = new java.util.HashMap<String,Object>();
        input.put("noise_reduction", Map.of("type", "near_field"));
        input.put("transcription", Map.of("model", "gpt-realtime-whisper"));
        input.put("turn_detection", prefs.noiseMode().equals("push_to_talk") ? null : Map.of(
            "type", "server_vad", "threshold", prefs.noiseMode().equals("noisy") ? 0.85 : 0.6,
            "prefix_padding_ms", 300, "silence_duration_ms", 900,
            "create_response", true, "interrupt_response", prefs.allowInterruptions()));

        var session = new java.util.HashMap<String, Object>();
        session.put("type", "realtime");
        session.put("model", model);
        session.put("instructions", BASE_INSTRUCTIONS + operation + AssistantPreferences.instructions(prefs));
        session.put("output_modalities", List.of("audio"));
        session.put("max_output_tokens", 800);
        session.put("audio", Map.of("input", input, "output", Map.of("voice", voice)));
        if (execute) {
            session.put("tools", List.of(
                Map.of("type", "function", "name", "run_command",
                    "description", "Run one noninteractive SSH command on the selected pod immediately and return its exit code and output.",
                    "parameters", runSchema())));
            session.put("tool_choice", "auto");
        }
        Map<String, Object> config = Map.of(
                "expires_after", Map.of("anchor", "created_at", "seconds", 60),
                "session", session);
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
            // Return the ephemeral token and applied preferences, never upstream metadata or the permanent key.
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(Map.of("value", key, "preferences", prefs, "execution", execute));
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
