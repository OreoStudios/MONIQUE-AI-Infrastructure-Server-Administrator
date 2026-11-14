package fr.oreostudios.monique.ai;

import fr.oreostudios.monique.pods.PodModels.PodView;
import fr.oreostudios.monique.pods.PodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
        """;

    private final PodService pods;
    private final RestClient client;
    private final String anthropicKey;
    private final String openaiKey;
    private final String claudeModel;
    private final String codexModel;

    public AiController(PodService pods, RestClient.Builder builder,
           @Value("${ANTHROPIC_API_KEY:}") String anthropicKey,
           @Value("${OPENAI_API_KEY:}") String openaiKey,
           @Value("${monique.claude-model}") String claudeModel,
           @Value("${monique.codex-model}") String codexModel) {
        this.pods = pods;
        this.client = builder.build();
        this.anthropicKey = anthropicKey;
        this.openaiKey = openaiKey;
        this.claudeModel = claudeModel;
        this.codexModel = codexModel;
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
            // Deliberately do not surface the provider response body, which may
            // contain sensitive context or upstream debugging information.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "AI provider request failed with HTTP " + e.getStatusCode().value());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "AI provider temporarily unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    private String askClaude(String input) {
        if (anthropicKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "ANTHROPIC_API_KEY is not configured");
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
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "OPENAI_API_KEY is not configured");
        }
        Map<String,Object> body = Map.of(
            "model", codexModel, "instructions", SYSTEM,
            "input", input, "max_output_tokens", 1800, "store", false
        );
        Map<String,Object> result = client.post()
            .uri("https://api.openai.com/v1/responses")
            .headers(h -> h.setBearerAuth(openaiKey))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body).retrieve().body(Map.class);
        if (result == null) throw new IllegalStateException("Empty provider response");
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
        if (text.isEmpty()) throw new IllegalStateException("No text from provider");
        return text.toString();
    }
}
