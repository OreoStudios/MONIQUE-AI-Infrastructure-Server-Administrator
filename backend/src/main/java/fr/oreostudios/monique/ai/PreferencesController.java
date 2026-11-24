package fr.oreostudios.monique.ai;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.Map;

@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {
    private final AssistantPreferences preferences;
    public PreferencesController(AssistantPreferences preferences) { this.preferences = preferences; }
    @GetMapping public AssistantPreferences.Preferences get() { return preferences.get(); }
    @PostMapping public AssistantPreferences.Preferences save(@RequestBody AssistantPreferences.Preferences p) { return preferences.save(p); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("code","PREFERENCES_INVALID","message",e.getMessage()));
    }
}
