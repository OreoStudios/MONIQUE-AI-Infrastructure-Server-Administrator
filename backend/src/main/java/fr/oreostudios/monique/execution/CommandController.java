package fr.oreostudios.monique.execution;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/execution")
public class CommandController {
    private final CommandService commands;
    public CommandController(CommandService commands) { this.commands = commands; }
    public record Proposal(UUID podId, String command, String purpose, int timeoutSeconds) { }
    @GetMapping("/pods/{podId}/trust") public CommandService.Trust trust(@PathVariable UUID podId) { return commands.trust(podId); }
    @PostMapping("/pods/{podId}/trust") public CommandService.Trust pin(@PathVariable UUID podId, @RequestBody CommandService.Trust trust) {
        commands.pin(podId,trust.fingerprint()); return commands.trust(podId);
    }
    @GetMapping("/pods/{podId}/jobs") public List<CommandService.Job> list(@PathVariable UUID podId) { return commands.list(podId); }
    @PostMapping("/jobs") public CommandService.Job propose(@RequestBody Proposal p) { return commands.propose(p.podId(),p.command(),p.purpose(),p.timeoutSeconds()); }
    @PostMapping("/jobs/{id}/approve") public CommandService.Job approve(@PathVariable UUID id) { return commands.approve(id); }
    @PostMapping("/jobs/{id}/reject") public CommandService.Job reject(@PathVariable UUID id) { return commands.reject(id); }
    @PostMapping("/jobs/{id}/acknowledge") public CommandService.Job acknowledge(@PathVariable UUID id) { return commands.acknowledge(id); }
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public Map<String,String> invalid(IllegalArgumentException e) { return Map.of("code","EXECUTION_INVALID","message",e.getMessage()); }
}
