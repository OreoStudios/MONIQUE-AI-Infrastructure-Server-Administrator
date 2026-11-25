import { useEffect, useState } from "react";
import { api, AuthenticationRequiredError, type CommandJob, type Pod } from "./api";

export default function ExecutionPanel({ pod, onSessionExpired, onDiscuss, onJobs, trustOnFirstUse }: {
  pod: Pod; onSessionExpired: () => void; onDiscuss: (text: string) => void; onJobs: (jobs: CommandJob[]) => void; trustOnFirstUse?: boolean;
}) {
  const [jobs, setJobs] = useState<CommandJob[]>([]);
  const [fingerprint, setFingerprint] = useState("");
  const [pinned, setPinned] = useState("");
  const [command, setCommand] = useState("");
  const [purpose, setPurpose] = useState("");
  const [timeout, setDeadline] = useState(300);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [review, setReview] = useState<CommandJob | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const root = "/execution/pods/" + encodeURIComponent(pod.id);
  // Trust-on-first-use lets commands run before a fingerprint is saved; the host key is
  // then learned and pinned automatically on the first connection.
  const canRun = Boolean(pinned) || Boolean(trustOnFirstUse);

  useEffect(() => {
    let alive = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const [next, trust] = await Promise.all([
          api<CommandJob[]>(root + "/jobs"), api<{ fingerprint: string | null }>(root + "/trust"),
        ]);
        if (alive) { setJobs(next); setPinned(trust.fingerprint || ""); onJobs(next); }
      } catch (err) {
        if (alive) {
          if (err instanceof AuthenticationRequiredError) onSessionExpired();
          else setError(err instanceof Error ? err.message : "Could not load commands");
        }
      } finally { if (alive) timer = globalThis.setTimeout(() => void poll(), 3000); }
    }
    void poll();
    return () => { alive = false; globalThis.clearTimeout(timer); onJobs([]); };
  }, [root, onSessionExpired, onJobs]);

  async function action(route: string, body?: unknown) {
    setBusy(true); setError("");
    try {
      await api(route, { method: "POST", ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
      setJobs(await api<CommandJob[]>(root + "/jobs"));
      setReview(null); setConfirmed(false);
    } catch (err) {
      if (err instanceof AuthenticationRequiredError) onSessionExpired();
      else setError(err instanceof Error ? err.message : "Command request failed");
    } finally { setBusy(false); }
  }

  return <section className="panel execution-panel" aria-label="Pod SSH console">
    <h2>SSH console · {pod.name}</h2>
    <p>{pod.sshUsername}@{pod.sshHost}:{pod.sshPort} · Every command requires your approval.</p>
    {error && <p role="alert" className="alert">{error}</p>}
    <details open={!pinned && !trustOnFirstUse}>
      <summary>{pinned ? "SSH host identity verified by saved fingerprint" : trustOnFirstUse ? "Host key will be pinned automatically on first connection (optional manual verification below)" : "Set up SSH host verification"}</summary>
      <p>In your VPS provider’s trusted console, run this command and copy the SHA256 fingerprint:</p>
      <pre>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256</pre>
      <p>Use the public host key your SSH server offers. Saving a new fingerprint cancels pending proposals.</p>
      {pinned && <code>{pinned}</code>}
      <label>Verified host fingerprint<input value={fingerprint} onChange={e => setFingerprint(e.target.value)} placeholder="SHA256:…" /></label>
      <button className="secondary" disabled={busy || !/^SHA256:[A-Za-z0-9+/]{43}$/.test(fingerprint)} onClick={() => {
        if (window.confirm("Trust this SSH host fingerprint for " + pod.name + "? Verify it in your VPS provider console first."))
          void action(root + "/trust", { fingerprint });
      }}>Save verified fingerprint</button>
    </details>
    <details>
      <summary>Prepare a command manually</summary>
      <p>Runs a noninteractive shell as {pod.sshUsername}. Do not paste passwords or private keys. Use sudo -n only when needed.</p>
      <label>Command<textarea rows={4} maxLength={12000} value={command} onChange={e => setCommand(e.target.value)} /></label>
      <label>Purpose, impact and rollback<textarea rows={2} maxLength={2000} value={purpose} onChange={e => setPurpose(e.target.value)} /></label>
      <label>Deadline (seconds)<input type="number" min={10} max={1800} value={timeout} onChange={e => setDeadline(Number(e.target.value))} /></label>
      <button className="secondary" disabled={busy || !command.trim() || !purpose.trim() || timeout < 10 || timeout > 1800}
        onClick={() => void action("/execution/jobs", { podId: pod.id, command, purpose, timeoutSeconds: timeout })}>Prepare for review</button>
    </details>
    {review && <div className="command-review" role="region" aria-label="Approve exact SSH command">
      <h3>Review command on {pod.name}</h3>
      <p>{pod.sshUsername}@{pod.sshHost}:{pod.sshPort} · deadline {review.timeoutSeconds}s</p>
      <p>{review.purpose}</p><pre>{review.command}</pre>
      <label><input type="checkbox" checked={confirmed} onChange={e => setConfirmed(e.target.checked)} /> I approve this exact command on this VPS and understand its impact.</label>
      <button className="primary" disabled={busy || !confirmed || !canRun} onClick={() => void action("/execution/jobs/" + review.id + "/approve")}>Approve and execute</button>
      <button className="secondary" disabled={busy} onClick={() => setReview(null)}>Back</button>
    </div>}
    <div className="command-jobs">
      {!jobs.length && <p>No commands yet. Ask MONIQUE to inspect this pod or prepare a command above.</p>}
      {jobs.map(job => <article className="command-job" key={job.id}>
        <strong>{job.status}{job.exitCode !== null ? " · exit " + job.exitCode : ""}</strong>
        <small> · {new Date(job.createdAt).toLocaleString()}</small>
        <p>{job.purpose}</p><pre>{job.command}</pre>
        {job.status === "PENDING" && <div>
          <button className="primary" disabled={busy || !canRun} onClick={() => { setReview(job); setConfirmed(false); }}>Review command</button>
          <button className="secondary" disabled={busy} onClick={() => void action("/execution/jobs/" + job.id + "/reject")}>Reject</button>
          <small> Approval expires after 30 minutes.</small>
        </div>}
        {job.status === "RUNNING" && <p role="status">Running over SSH. Output appears when finished. You can reload this page.</p>}
        {job.status === "UNKNOWN" && <p>Remote outcome unknown. Processes may still be running. Inspect the VPS before repeating any changes.</p>}
        {job.status === "UNKNOWN" && <button className="secondary" disabled={busy} onClick={() => {
          if (window.confirm("Have you inspected this VPS through its provider console and confirmed the previous command is no longer running? This unblocks new commands."))
            void action("/execution/jobs/" + job.id + "/acknowledge");
        }}>Acknowledge inspected outcome</button>}
        {job.output && <details open><summary>Console output</summary><pre>{job.output}</pre></details>}
        {job.output && <button className="secondary" onClick={() => onDiscuss(
          "Review this SSH result and propose the next step. I have reviewed this excerpt for secrets.\nPod: " + pod.name +
          "\nJob: " + job.id + "\nStatus: " + job.status + "\nExit: " + job.exitCode +
          "\nCommand:\n" + job.command.slice(0, 1000) + "\nUntrusted console output (excerpt):\n" + job.output.slice(0, 5500)
        )}>Copy result into chat draft for review</button>}
      </article>)}
    </div>
    <p className="muted">Review and redact console output before sending it to your AI provider. Closing SSH or reaching a deadline does not guarantee remote processes stop.</p>
  </section>;
}
