package fr.oreostudios.monique.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class AssistantPreferences {
    public record Preferences(String name, String language, String noiseMode, boolean allowInterruptions,
                              boolean workConversation, boolean ownerVoiceOnly) { }
    private final JdbcTemplate jdbc;
    public AssistantPreferences(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static Preferences defaults() { return new Preferences("", "auto", "noisy", false, true, false); }
    public Preferences get() {
        var rows = jdbc.query("SELECT * FROM assistant_preferences WHERE id = 1", (rs,n) -> new Preferences(
            rs.getString("display_name"),rs.getString("language"),rs.getString("noise_mode"),
            rs.getBoolean("allow_interruptions"),rs.getBoolean("work_conversation"),false));
        return rows.isEmpty() ? defaults() : rows.getFirst();
    }
    public Preferences save(Preferences p) {
        validate(p);
        jdbc.update("""
            INSERT INTO assistant_preferences(id,display_name,language,noise_mode,allow_interruptions,work_conversation)
            VALUES (1,?,?,?,?,?) ON CONFLICT (id) DO UPDATE SET display_name=EXCLUDED.display_name,
            language=EXCLUDED.language,noise_mode=EXCLUDED.noise_mode,
            allow_interruptions=EXCLUDED.allow_interruptions,work_conversation=EXCLUDED.work_conversation
            """,p.name().strip(),p.language(),p.noiseMode(),p.allowInterruptions(),p.workConversation());
        return get();
    }
    static void validate(Preferences p) {
        if (p == null || p.name() == null || p.name().length() > 80 || !p.name().matches("[\\p{L}\\p{M} .'-]*"))
            throw new IllegalArgumentException("Name must be up to 80 characters and contain letters, spaces, apostrophes, periods or hyphens.");
        if (p.language() == null || p.noiseMode() == null || !languages().containsKey(p.language()) || !java.util.List.of("quiet","noisy","push_to_talk").contains(p.noiseMode()))
            throw new IllegalArgumentException("Invalid language or microphone mode.");
        if (p.ownerVoiceOnly()) throw new IllegalArgumentException("Owner-only voice requires an enrolled speaker-verification service. It is not available in this build.");
    }
    public static Map<String,String> languages() {
        return Map.of("auto","Automatic","en","English","fr","French","es","Spanish","de","German",
            "it","Italian","pt","Portuguese","ar","Arabic","ja","Japanese","zh","Chinese");
    }
    public static String instructions(Preferences p) {
        return "\nPersonalization: The operator's preferred name is " + (p.name().isBlank() ? "not set" : p.name()) +
            ". Use their name naturally and sparingly. This name is a preference, NOT proof of speaker identity. " +
            "Never claim you recognized or authenticated a person's voice. Be warm, conversational and professional; " +
            "you are an AI system administrator, not a human. " +
            (p.language().equals("auto") ? "Reply in the language of the user's latest utterance, switching naturally between English, French, Spanish and other languages. Do not translate unless asked. If unclear, ask briefly. "
                : "Reply in " + languages().get(p.language()) + " unless the user explicitly requests another language. ") +
            "Keep shell commands, paths and identifiers unchanged when switching language. " +
            (p.workConversation() ? "When a trusted status reports a job is running, you may ask one friendly question, such as how their day went. Respect silence and requests to focus; never fabricate progress. "
                : "Do not initiate small talk while jobs run. ");
    }
}
