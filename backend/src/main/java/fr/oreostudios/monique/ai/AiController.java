package fr.oreostudios.monique.ai;

import fr.oreostudios.monique.pods.PodModels.PodView;
import fr.oreostudios.monique.pods.PodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private static final String SYSTEM = """
        You are MONIQUE, an experienced, cautious infrastructure administrator.
        You can propose an operations plan, but you HAVE NO EXECUTION TOOLS.
        NEVER claim you have connected, inspected, installed, migrated, deployed or
        verified anything. You must clearly distinguish known data from assumptions.
        Never invent SSH results, backups, IP assignments or running service versions.
        For installs and migrations, specify preflight checks, conflict analysis,
        preservation of existing services, backup/restore, approvals and verification.
        If an action could disrupt Pterodactyl, Wings, Coolify, Docker, a database,
        firewall, reverse proxy or remote access, flag it and require authorization.
        Never request or reveal credentials, SSH private keys, tokens or passwords.
        If the user asks for action, outline what the MONIQUE execution engine
        will need to do when implemented. It is NOT implemented yet.
        Give practical, concise steps, and be transparent about limitations.
        For infrastructure operations, use a disciplined change-plan format:
        (1) existing facts versus unverified assumptions, (2) preflight read-only
        checks, (3) risks and dependencies, (4) a proposed minimal-change sequence,
        (5) rollback/recovery, (6) validation and explicit operator approval.
        Prefer least privilege and reversible changes. Never propose destructive
        actions without impact analysis, tested backups, and confirmation.
        When the question is simple, answer concisely instead of adding a checklist.
        Your reasoning ability does not grant direct access to hosts, logs or tools.
        """;

    private final PodService pods;
    private final RestClient client;
    private final String anthropicKey;
    private final String openaiKey;
    private final String claudeModel;
    private final String codexModel;
    private final String codexReasoningEffort;
    private final int codexMaxOutputTokens;

    public AiController(PodService pods, RestClient.Builder builder,
           @Value("${ANTHROPIC_API_KEY:}") String anthropicKey,
           @Value("${OPENAI_API_KEY:}") String openaiKey,
           @Value("${monique.claude-model}") String claudeModel,
           @Value("${monique.codex-model}") String codexModel,
           @Value("${monique.codex-reasoning-effort:max}") String codexReasoningEffort,
           @Value("${monique.codex-max-output-tokens:16000}") int codexMaxOutputTokens) {
        this.pods = pods;
        this.client = builder.build();
        this.anthropicKey = anthropicKey;
        this.openaiKey = openaiKey;
        this.claudeModel = claudeModel;
        this.codexModel = codexModel;
        if (!List.of("low", "medium", "high", "xhigh", "max").contains(codexReasoningEffort)) {
            throw new IllegalArgumentException("Unsupported MONIQUE_CODEX_REASONING_EFFORT");
        }
        if (codexMaxOutputTokens < 2048 || codexMaxOutputTokens > 64000) {
            throw new IllegalArgumentException("MONIQUE_CODEX_MAX_OUTPUT_TOKENS must be 2048..64000");
        }
        this.codexReasoningEffort = codexReasoningEffort;
        this.codexMaxOutputTokens = codexMaxOutputTokens;
    }

    /** Only vetted public messages leave the API; never return upstream bodies. */
    static final class ProviderFailure extends RuntimeException {
        final HttpStatus status;
        final String code;
        ProviderFailure(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    @ExceptionHandler(ProviderFailure.class)
    ResponseEntity<Map<String, String>> handleProviderFailure(ProviderFailure failure) {
        return ResponseEntity.status(failure.status)
            .body(Map.of("code", failure.code, "message", failure.getMessage()));
    }

    private ProviderFailure upstreamFailure(String provider, RestClientResponseException e) {
        String prefix = provider.equals("CODEX") ? "OPENAI" : "ANTHROPIC";
        String name = provider.equals("CODEX") ? "OpenAI" : "Anthropic";
        int status = e.getStatusCode().value();
        return switch (status) {
            case 400 -> new ProviderFailure(HttpStatus.BAD_GATEWAY, prefix + "_BAD_REQUEST",
                name + " rejected the request (HTTP 400). Verify the model and request settings.");
            case 401 -> new ProviderFailure(HttpStatus.BAD_GATEWAY, prefix + "_KEY_REJECTED",
                name + " rejected the provider API key. Check the provider key in Coolify; your MONIQUE admin login is unrelated.");
            case 403 -> new ProviderFailure(HttpStatus.BAD_GATEWAY, prefix + "_ACCESS_DENIED",
                name + " denied API access. Verify project permissions and model access.");
            case 404 -> new ProviderFailure(HttpStatus.BAD_GATEWAY, prefix + "_MODEL_UNAVAILABLE",
                name + " could not find the configured model. Check model availability and MONIQUE_CODEX_MODEL in Coolify.");
            case 429 -> e.getResponseBodyAsString().contains("insufficient_quota")
                ? new ProviderFailure(HttpStatus.BAD_GATEWAY, prefix + "_BILLING_LIMIT",
                    name + " reports insufficient API quota. Check project billing, balance, and spending limits.")
                : new ProviderFailure(HttpStatus.TOO_MANY_REQUESTS, prefix + "_RATE_LIMIT",
                    name + " is rate-limiting requests. Retry after the provider limit resets.");
            default -> new ProviderFailure(HttpStatus.BAD_GATEWAY, prefix + "_UNAVAILABLE",
                name + " request failed (upstream HTTP " + status + ").");
        };
    }

    public record ChatRequest(
        @NotBlank @Size(max=8000) String message,
        String provider,
        List<UUID> podIds
    ) { }

    public record ChatResponse(String provider, String model, String answer, boolean executed) { }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String provider = request.provider() == null ? "CLAUDE" :
                          request.provider().toUpperCase(Locale.ROOT);
        if (!List.of("CLAUDE", "CODEX").contains(provider)) {
            throw new IllegalArgumentException("provider must be CLAUDE or CODEX");
        }
        if (request.podIds() != null && request.podIds().size() > 20) {
            throw new IllegalArgumentException("Maximum 20 pods in a chat request");
        }

        // Only public inventory metadata is passed to an LLM. Never credentials,
        // private keys, ciphertext, API tokens, SSH response bodies or audit secrets.
        StringBuilder context = new StringBuilder("Registered inventory (not live host inspection):");
        if (request.podIds() != null) {
            for (UUID id : request.podIds().stream().distinct().toList()) {
                PodView p = pods.find(id);
                context.append("\n- ").append(p.name())
                       .append(" (").append(p.id()).append("), ")
                       .append(p.addresses().size()).append(" configured IPs");
            }
        }
        String input = context + "\n\nOperator's request:\n" + request.message();
        String model = provider.equals("CLAUDE") ? claudeModel : codexModel;

        try {
            String answer = provider.equals("CLAUDE") ? askClaude(input) : askCodex(input);
            return new ChatResponse(provider, model, answer, false);
        } catch (RestClientResponseException e) {
            throw upstreamFailure(provider, e);
        } catch (ProviderFailure e) {
            throw e;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderFailure(HttpStatus.BAD_GATEWAY,
                provider.equals("CODEX") ? "OPENAI_UNAVAILABLE" : "ANTHROPIC_UNAVAILABLE",
                "The selected AI provider is temporarily unavailable. Check MONIQUE backend logs for connectivity problems.");
        }
    }

    @SuppressWarnings("unchecked")
    private String askClaude(String input) {
        if (anthropicKey.isBlank()) {
            throw new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE, "ANTHROPIC_KEY_MISSING",
                "ANTHROPIC_API_KEY is not configured in the MONIQUE backend (Coolify).");
        }
        Map<String, Object> body = Map.of(
            "model", claudeModel, "max_tokens", 1800, "system", SYSTEM,
            "messages", List.of(Map.of("role", "user", "content", input))
        );
        Map<String,Object> result = client.post()
            .uri("https://api.anthropic.com/v1/messages")
            .header("x-api-key", anthropicKey)
            .header("anthropic-version", "2023-06-01")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body).retrieve().body(Map.class);
        if (result == null) throw new IllegalStateException("Empty provider response");
        List<Map<String,Object>> content = (List<Map<String,Object>>) result.get("content");
        if (content == null) throw new IllegalStateException("Missing provider content");
        return content.stream().filter(c -> "text".equals(c.get("type")))
            .map(c -> String.valueOf(c.get("text")))
            .reduce("", (a,b) -> a + b);
    }

    @SuppressWarnings("unchecked")
    private String askCodex(String input) {
        if (openaiKey.isBlank()) {
            throw new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE, "OPENAI_KEY_MISSING",
                "OPENAI_API_KEY is not configured in the MONIQUE backend (Coolify).");
        }
        Map<String,Object> body = Map.of(
            "model", codexModel, "instructions", SYSTEM,
            "input", input,
            "reasoning", Map.of("effort", codexReasoningEffort),
            "max_output_tokens", codexMaxOutputTokens,
            "store", false
        );
        Map<String,Object> result = client.post()
            .uri("https://api.openai.com/v1/responses")
            .headers(h -> h.setBearerAuth(openaiKey))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body).retrieve().body(Map.class);
        if (result == null) throw new IllegalStateException("Empty provider response");
        if ("incomplete".equals(result.get("status"))) {
            throw new ProviderFailure(HttpStatus.BAD_GATEWAY, "OPENAI_OUTPUT_LIMIT",
                "OpenAI exhausted its response token allowance while reasoning. Increase MONIQUE_CODEX_MAX_OUTPUT_TOKENS or reduce reasoning effort.");
        }
        List<Map<String,Object>> output = (List<Map<String,Object>>) result.get("output");
        if (output == null) throw new IllegalStateException("Missing provider output");
        StringBuilder text = new StringBuilder();
        for (Map<String,Object> item : output) {
            Object list = item.get("content");
            if (!(list instanceof List<?> contents)) continue;
            for (Object part : contents) {
                if (part instanceof Map<?,?> map && "output_text".equals(map.get("type"))) {
                    text.append(map.get("text"));
                }
            }
        }
        if (text.isEmpty()) {
            throw new ProviderFailure(HttpStatus.BAD_GATEWAY, "OPENAI_EMPTY_RESPONSE",
                "OpenAI returned no visible answer. Verify model access and increase the reasoning output budget.");
        }
        return text.toString();
    }
}
