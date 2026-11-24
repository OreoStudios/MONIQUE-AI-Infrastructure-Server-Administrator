import { useEffect, useState } from "react";
import { api, AuthenticationRequiredError, type AssistantPreferences } from "./api";

export default function PreferencesPanel({ onSessionExpired }: { onSessionExpired: () => void }) {
  const [prefs, setPrefs] = useState<AssistantPreferences | null>(null);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    let active = true;
    void api<AssistantPreferences>("/preferences").then(p => { if (active) setPrefs(p); }).catch(e => {
      if (!active) return;
      if (e instanceof AuthenticationRequiredError) onSessionExpired();
      else setError(e instanceof Error ? e.message : "Could not load preferences");
    });
    return () => { active = false; };
  }, [onSessionExpired]);
  function change(p: Partial<AssistantPreferences>) { setPrefs(previous => previous && { ...previous, ...p }); setSaved(false); }
  return <div className="content"><h1>MONIQUE settings</h1>
    <p className="subtitle">Personalize how MONIQUE addresses you and listens. These settings belong to this MONIQUE installation.</p>
    {error && <p role="alert" className="alert">{error}</p>}
    {!prefs ? <p>Loading preferences...</p> : <form className="panel pod-form preferences-form" onSubmit={async e => {
      e.preventDefault(); setBusy(true); setError(""); setSaved(false);
      try { setPrefs(await api<AssistantPreferences>("/preferences", { method: "POST", body: JSON.stringify(prefs) })); setSaved(true); }
      catch (err) { if (err instanceof AuthenticationRequiredError) onSessionExpired(); else setError(err instanceof Error ? err.message : "Could not save preferences"); }
      finally { setBusy(false); }
    }}>
      <label>Your name<input maxLength={80} value={prefs.name} onChange={e => change({ name: e.target.value })} placeholder="Elias" autoComplete="given-name" /></label>
      <label>Conversation language<select value={prefs.language} onChange={e => change({ language: e.target.value })}>
        {Object.entries({ auto: "Automatic — match my language", en: "English", fr: "Français", es: "Español", de: "Deutsch", it: "Italiano", pt: "Português", ar: "العربية", ja: "日本語", zh: "中文" }).map(([id,label]) => <option key={id} value={id}>{label}</option>)}
      </select></label>
      <p>Automatic mode follows the language you speak or type, including languages beyond this list. Dashboard labels remain in English.</p>
      <label>Microphone mode<select value={prefs.noiseMode} onChange={e => change({ noiseMode: e.target.value as AssistantPreferences["noiseMode"] })}>
        <option value="noisy">Noisy room — stronger speech threshold</option><option value="quiet">Quiet room — more sensitive</option><option value="push_to_talk">Push to talk — listen only while held</option>
      </select></label>
      <label><input type="checkbox" checked={prefs.allowInterruptions} disabled={prefs.noiseMode === "push_to_talk"} onChange={e => change({ allowInterruptions: e.target.checked })} /> Let my speech interrupt MONIQUE</label>
      <p>Off by default so a chair or background sound does not cut her off. Use “Interrupt” to stop her deliberately.</p>
      <label><input type="checkbox" checked={prefs.workConversation} onChange={e => change({ workConversation: e.target.checked })} /> Start a friendly conversation while a command runs</label>
      <p>With live voice connected, MONIQUE may ask one short question during a quiet moment. She does not invent installation progress or repeatedly interrupt you.</p>
      <label>Who can speak?<select value="anyone" disabled><option value="anyone">Anyone using the active microphone</option><option value="owner">Only my enrolled voice — not available</option></select></label>
      <p>Names do not verify voices. Owner-only listening needs a separate speaker-verification service and voice enrollment. This build does not identify speakers or reject other people by voice.</p>
      <button className="primary" disabled={busy} type="submit">{busy ? "Saving..." : "Save settings"}</button>
      {saved && <p role="status">Saved. New chat replies use these preferences. Restart live voice to apply them.</p>}
    </form>}
  </div>;
}
