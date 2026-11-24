package fr.oreostudios.monique.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final JdbcTemplate jdbc;
    private final fr.oreostudios.monique.execution.CommandService commands;
    public ApiController(JdbcTemplate jdbc, fr.oreostudios.monique.execution.CommandService commands) { this.jdbc = jdbc; this.commands = commands; }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("name", "MONIQUE", "version", "0.2.0", "mode", commands.enabled() ? "APPROVAL_REQUIRED" : "PLANNING_ONLY",
                      "executionEnabled", commands.enabled());
    }

    @GetMapping("/audit")
    public List<Map<String,Object>> audit() {
        return jdbc.query("""
            SELECT id, at, event, pod_id, details FROM audit_events ORDER BY at DESC LIMIT 100
            """, (rs, rowNum) -> {
                var event = new java.util.LinkedHashMap<String,Object>();
                event.put("id", rs.getObject("id", UUID.class));
                event.put("at", rs.getObject("at", OffsetDateTime.class).toInstant());
                event.put("event", rs.getString("event"));
                event.put("podId", rs.getObject("pod_id", UUID.class));
                event.put("details", rs.getString("details"));
                return event;
            });
    }
}

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badArgument(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalidBody() {
        return Map.of("error", "Invalid or missing request fields");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> invalidJson() {
        return Map.of("error", "Invalid JSON payload");
    }
}
