import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { api, loginAdmin, restoreAdmin, logoutAdmin, clearAdminSession, AuthenticationRequiredError, type ChatResponse, type Pod, type Status } from "./api";
import { moniqueAvatar } from "./moniqueAvatar";
import VoicePanel from "./VoicePanel";

type View = "overview" | "pods" | "chat";
type Message = { role: "assistant" | "user"; content: string };

const emptyPod = {
  name: "",
  sshHost: "",
  sshPort: 22,
  sshUsername: "",
  credentialType: "PASSWORD",
  credential: "",
  addresses: "",
};

/**
 * The main Overview (and login) use the original, full-bleed typing video.
 * Chat has its own compact portrait video with distinct idle/reply states.
 */
function MoniqueBackdrop() {
  return (
    <div className="monique-backdrop" aria-hidden="true">
      <video
        className="monique-backdrop-video"
        autoPlay
        loop
        muted
        playsInline
        preload="auto"
        disablePictureInPicture
        poster={moniqueAvatar}
      >
        {/* Full-bleed background: never select the desktop letterboxed composite.
            Cover/crop is handled by CSS for every viewport size. */}
        <source src="/media/monique-typing.mp4" type="video/mp4" />
      </video>
      <div className="monique-backdrop-wash" />
    </div>
  );
}

/**
 * MONIQUE's video portrait, positioned beside chat content.
 * Idle: original typing clip on a native loop.
 * Reply: the user's talking+typing clip, replayed from the beginning per request.
 * Both clips are muted: the video does not claim to lip-sync AI-generated text.
 */
function MoniqueChatVideo({ isReplying }: { isReplying: boolean }) {
  const replyRef = useRef<HTMLVideoElement>(null);
  const [replyAvailable, setReplyAvailable] = useState(true);
  const showReply = isReplying && replyAvailable;

  useEffect(() => {
    const video = replyRef.current;
    if (!video) return;
    if (showReply) {
      video.currentTime = 0;
      void video.play().catch(() => setReplyAvailable(false));
    } else {
      video.pause();
      video.currentTime = 0;
    }
  }, [showReply]);

  return (
    <aside className="chat-video-panel" aria-label="MONIQUE AI animated portrait">
      <div className="chat-video-stage">
        <video
          className="chat-avatar-video idle"
          autoPlay
          muted
          loop
          playsInline
          preload="auto"
          disablePictureInPicture
          poster={moniqueAvatar}
          aria-hidden="true"
        >
          <source src="/media/monique-typing.mp4" type="video/mp4" />
        </video>
        <video
          ref={replyRef}
          className={showReply ? "chat-avatar-video speaking is-visible" : "chat-avatar-video speaking"}
          muted
          loop
          playsInline
          preload="auto"
          disablePictureInPicture
          poster={moniqueAvatar}
          onError={() => setReplyAvailable(false)}
          aria-hidden="true"
        >
          <source src="/media/monique-typing-speaking.mp4" type="video/mp4" />
        </video>
        <div className="chat-video-shade" />
        <div className="chat-video-topline"><span className="chat-video-pulse" /> MONIQUE AI</div>
        <div className="chat-video-caption">
          <strong>{isReplying ? "MONIQUE is responding" : "MONIQUE is ready"}</strong>
          <span>{isReplying ? "Preparing your answer…" : "Waiting for your message"}</span>
        </div>
      </div>
    </aside>
  );
}

function MoniqueChatAvatar() {
  return <span className="message-avatar monique-message-avatar" aria-label="MONIQUE AI">
    <img src={moniqueAvatar} alt="" width={40} height={40} draggable={false} />
  </span>;
}

export default function App() {
  const [draftToken, setDraftToken] = useState("");
  const [authenticated, setAuthenticated] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const [status, setStatus] = useState<Status | null>(null);
  const [pods, setPods] = useState<Pod[]>([]);
  const [view, setView] = useState<View>("overview");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [chatPending, setChatPending] = useState(false);
  const [voiceSpeaking, setVoiceSpeaking] = useState(false);
  const [podForm, setPodForm] = useState(emptyPod);
  const [showPodForm, setShowPodForm] = useState(false);
  const [provider, setProvider] = useState<"CLAUDE" | "CODEX">("CLAUDE");
  const [selectedPod, setSelectedPod] = useState("");
  const [prompt, setPrompt] = useState("");
  const [messages, setMessages] = useState<Message[]>([
    { role: "assistant", content: "Hi, I'm MONIQUE. I can help plan infrastructure operations safely. Actual SSH execution is not available in this preview." },
  ]);

  const recordVoiceTranscript = useCallback((role: Message["role"], content: string) => {
    setMessages(previous => [...previous, { role, content }]);
  }, []);

  const sessionExpired = useCallback(() => {
    clearAdminSession();
    setAuthenticated(false);
    setStatus(null);
    setPods([]);
    setBusy(false);
    setChatPending(false);
    setVoiceSpeaking(false);
    setError("Your administrator session expired or was revoked. Please sign in again.");
  }, []);

  function showRequestError(err: unknown, fallback: string) {
    if (err instanceof AuthenticationRequiredError) {
      sessionExpired();
    } else {
      setError(err instanceof Error ? err.message : fallback);
    }
  }

  async function refresh() {
    const [nextStatus, nextPods] = await Promise.all([
      api<Status>("/status"),
      api<Pod[]>("/pods"),
    ]);
    setStatus(nextStatus);
    setPods(nextPods);
  }

  // Restore an HttpOnly cookie session after a reload. No admin token is
  // persisted in localStorage or sessionStorage.
  useEffect(() => {
    let canceled = false;
    void (async () => {
      try {
        await restoreAdmin();
        const [nextStatus, nextPods] = await Promise.all([
          api<Status>("/status"),
          api<Pod[]>("/pods"),
        ]);
        if (!canceled) {
          setStatus(nextStatus);
          setPods(nextPods);
          setAuthenticated(true);
        }
      } catch (err) {
        if (!canceled && !(err instanceof AuthenticationRequiredError)) {
          setError("Could not verify the administrator session. Try signing in again.");
        }
      } finally {
        if (!canceled) setRestoring(false);
      }
    })();
    return () => { canceled = true; };
  }, []);

  async function connect(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await loginAdmin(draftToken);
      setDraftToken("");
      setAuthenticated(true);
      await refresh();
    } catch (err) {
      showRequestError(err, "Could not connect");
    } finally {
      setBusy(false);
    }
  }

  function disconnect() {
    // Start logout while the CSRF token is still in memory, then unmount
    // authenticated screens and stop any active voice conversation.
    void logoutAdmin().catch(() => {});
    setAuthenticated(false);
    setStatus(null);
    setPods([]);
    setMessages([{ role: "assistant", content: "Welcome back. Connect to begin a new planning conversation." }]);
    setPodForm(emptyPod);
    setPrompt("");
    setError("");
    setBusy(false);
    setChatPending(false);
    setVoiceSpeaking(false);
  }

  async function addPod(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const addresses = podForm.addresses
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .map((line) => {
          const separator = line.lastIndexOf("=");
          if (separator < 1) {
            throw new Error("Each address must use IP=ROLE");
          }
          return {
            address: line.slice(0, separator).trim(),
            role: line.slice(separator + 1).trim().toUpperCase(),
          };
        });
      await api<Pod>("/pods", {
        method: "POST",
        body: JSON.stringify({
          ...podForm,
          sshPort: Number(podForm.sshPort),
          addresses,
        }),
      });
      setPodForm(emptyPod);
      setShowPodForm(false);
      await refresh();
    } catch (err) {
      showRequestError(err, "Could not register Pod");
    } finally {
      setBusy(false);
    }
  }

  async function deletePod(pod: Pod) {
    if (!window.confirm("Delete the inventory record for " + pod.name + "? The remote server will NOT be changed.")) {
      return;
    }
    setBusy(true);
    setError("");
    try {
      await api<void>("/pods/" + encodeURIComponent(pod.id), { method: "DELETE" });
      await refresh();
    } catch (err) {
      showRequestError(err, "Could not remove Pod");
    } finally {
      setBusy(false);
    }
  }

  async function sendChat(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const text = prompt.trim();
    if (!text || busy) return;
    setPrompt("");
    setMessages((previous) => [...previous, { role: "user", content: text }]);
    setBusy(true);
    setChatPending(true);
    setError("");
    try {
      const response = await api<ChatResponse>("/ai/chat", {
        method: "POST",
        body: JSON.stringify({
          message: text,
          provider,
          podIds: selectedPod ? [selectedPod] : [],
        }),
      });
      setMessages((previous) => [...previous, {
        role: "assistant",
        content: response.answer + (response.executed ? "" : "\n\n[Planning only — nothing was executed]"),
      }]);
    } catch (err) {
      if (err instanceof AuthenticationRequiredError) {
        sessionExpired();
      } else {
        showRequestError(err, "AI provider could not answer");
        setMessages((previous) => [...previous, {
          role: "assistant", content: "The request failed. Check your configured AI provider key and MONIQUE connection.",
        }]);
      }
    } finally {
      setChatPending(false);
      setBusy(false);
    }
  }

  if (restoring) return <div className="login-page"><MoniqueBackdrop /><p className="session-check">Restoring your MONIQUE session…</p></div>;

  if (!authenticated) {
    return (
      <div className="login-page">
        <MoniqueBackdrop />
        <div className="login-glow" />
        <form className="login-card" onSubmit={(event) => void connect(event)}>
          <div className="logo large">M</div>
          <p className="eyebrow">OREO STUDIOS / INFRASTRUCTURE AI</p>
          <h1>Meet MONIQUE.</h1>
          <p className="muted">Your self-hosted infrastructure assistant. Connect to the local development control plane to manage Pod inventory and plan safe changes.</p>
          <label htmlFor="admin-token">MONIQUE admin token</label>
          <input
            id="admin-token"
            type="password"
            autoComplete="off"
            placeholder="Paste your existing admin token"
            value={draftToken}
            onChange={(event) => setDraftToken(event.target.value)}
            required
            minLength={32}
          />
          <button className="primary full" type="submit" disabled={busy}>
            {busy ? "Connecting…" : "Connect to MONIQUE"}
          </button>
          {error && <p className="alert" role="alert">{error}</p>}
          <p className="hint">The admin token is used only to sign in. Your 12-hour session is stored in a secure HttpOnly cookie; no admin token is saved in browser storage.</p>
        </form>
      </div>
    );
  }

  return (
    <div className="shell">
      {view === "overview" && <MoniqueBackdrop />}
      <aside className="sidebar">
        <div className="brand"><div className="logo">M</div><span>MONIQUE <small>AI INFRASTRUCTURE</small></span></div>
        <div className="nav-label">WORKSPACE</div>
        <nav aria-label="Main navigation">
          <button className={view === "overview" ? "nav active" : "nav"} onClick={() => setView("overview")}>◈ <span>Overview</span></button>
          <button className={view === "pods" ? "nav active" : "nav"} onClick={() => setView("pods")}>▣ <span>Pods</span><em>{pods.length}</em></button>
          <button className={view === "chat" ? "nav active" : "nav"} onClick={() => setView("chat")}>◇ <span>AI Chat</span></button>
        </nav>
        <div className="sidebar-bottom">
          <div className="security-note"><span className="dot" /> Planning-only preview<br /><small>Remote execution disabled</small></div>
          <button className="nav" onClick={disconnect}>↪ <span>Disconnect</span></button>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <span className="breadcrumb">Workspace <span>/</span> {view === "overview" ? "Overview" : view === "pods" ? "Pods" : "MONIQUE Chat"}</span>
          <div className="topbar-right"><span className="status-chip"><i /> Control plane connected</span><span className="avatar">OS</span></div>
        </header>

        {error && <div className="alert page-alert" role="alert">{error}<button onClick={() => setError("")} aria-label="Dismiss error">×</button></div>}

        {view === "overview" && (
          <div className="content">
            <p className="eyebrow">INFRASTRUCTURE COMMAND CENTER</p>
            <h1>Good to have you here.</h1>
            <p className="subtitle">MONIQUE keeps your infrastructure inventory organized while you plan safe operations.</p>
            <div className="stats">
              <div className="stat-card"><span>Total registered Pods</span><strong>{pods.length}</strong><small>Stored inventory, not live scan</small></div>
              <div className="stat-card"><span>Control plane</span><strong className="smaller">{status?.version ?? "—"}</strong><small>{status?.name ?? "MONIQUE"}</small></div>
              <div className="stat-card"><span>Execution status</span><strong className="smaller">Disabled</strong><small>{status?.mode ?? "PLANNING_ONLY"}</small></div>
            </div>
            <div className="two-cards">
              <section className="panel">
                <div className="panel-icon">◇</div><h2>Talk to MONIQUE</h2>
                <p>Ask for installation plans, migration checklists or a risk review. Suggestions require human approval before future execution.</p>
                <button className="secondary" onClick={() => setView("chat")}>Open AI Chat →</button>
              </section>
              <section className="panel">
                <div className="panel-icon">▣</div><h2>Manage your Pods</h2>
                <p>Register and review your VPS and dedicated servers. Credentials are encrypted by the backend and never listed here.</p>
                <button className="secondary" onClick={() => setView("pods")}>Open Pod Manager →</button>
              </section>
            </div>
            <div className="notice"><strong>Development preview.</strong> The CLI, desktop app, subscriptions and remote execution engine are under active development. Nothing on this page runs SSH commands.</div>
          </div>
        )}

        {view === "pods" && (
          <div className="content">
            <p className="eyebrow">YOUR INFRASTRUCTURE</p>
            <div className="heading-row"><div><h1>Connected Pods</h1><p className="subtitle">Manage the inventory of servers MONIQUE will eventually administer.</p></div>
              <button className="primary" onClick={() => setShowPodForm((previous) => !previous)}>{showPodForm ? "Close form" : "+ Add Pod"}</button>
            </div>
            {showPodForm && (
              <form className="panel pod-form" onSubmit={(event) => void addPod(event)}>
                <h2>Register a new Pod</h2>
                <p className="muted">This saves inventory metadata only. No SSH connection will be attempted.</p>
                <div className="form-grid">
                  <label>Pod name<input value={podForm.name} required maxLength={100} onChange={(e) => setPodForm({ ...podForm, name: e.target.value })} placeholder="Production VPS" /></label>
                  <label>Host/IP<input value={podForm.sshHost} required maxLength={255} onChange={(e) => setPodForm({ ...podForm, sshHost: e.target.value })} placeholder="192.0.2.10" /></label>
                  <label>SSH username<input value={podForm.sshUsername} required onChange={(e) => setPodForm({ ...podForm, sshUsername: e.target.value })} placeholder="deploy" /></label>
                  <label>SSH port<input value={podForm.sshPort} type="number" min={1} max={65535} required onChange={(e) => setPodForm({ ...podForm, sshPort: Number(e.target.value) })} /></label>
                  <label>Credential type<select value={podForm.credentialType} onChange={(e) => setPodForm({ ...podForm, credentialType: e.target.value })}><option value="PASSWORD">Password</option><option value="PRIVATE_KEY">Private key</option></select></label>
                  <label className="span-2">{podForm.credentialType === "PRIVATE_KEY" ? "Private SSH key" : "SSH password"}
                    {podForm.credentialType === "PRIVATE_KEY" ?
                      <textarea rows={4} value={podForm.credential} required onChange={(e) => setPodForm({ ...podForm, credential: e.target.value })} autoComplete="off" /> :
                      <input type="password" value={podForm.credential} required onChange={(e) => setPodForm({ ...podForm, credential: e.target.value })} autoComplete="new-password" />}
                  </label>
                  <label className="span-2">IP addresses (optional; one IP=ROLE per line)
                    <textarea rows={3} placeholder="192.0.2.10=PRIMARY" value={podForm.addresses} onChange={(e) => setPodForm({ ...podForm, addresses: e.target.value })} />
                  </label>
                </div>
                <button className="primary" type="submit" disabled={busy}>{busy ? "Saving…" : "Register Pod"}</button>
              </form>
            )}
            <section className="panel pod-table">
              {pods.length ? pods.map((pod) => (
                <div className="pod-row" key={pod.id}>
                  <div className="server-icon">▣</div>
                  <div className="pod-meta"><strong>{pod.name}</strong><small>{pod.sshHost}:{pod.sshPort} · {pod.sshUsername} · {pod.addresses.length} IPs</small></div>
                  <span className="inventory-chip">Inventory</span>
                  <button className="text-danger" disabled={busy} onClick={() => void deletePod(pod)}>Remove</button>
                </div>
              )) : <div className="empty"><div className="empty-icon">▣</div><h2>No Pods yet</h2><p>Add a test server to begin organizing your infrastructure.</p></div>}
            </section>
          </div>
        )}

        {view === "chat" && (
          <div className="chat-workspace">
            <MoniqueChatVideo isReplying={chatPending || voiceSpeaking} />
            <div className="content chat-layout">
            <div className="chat-heading"><div><p className="eyebrow">AI INFRASTRUCTURE ASSISTANT</p><h1>Talk to MONIQUE</h1></div><span className="status-chip caution">Planning only</span></div>
            <div className="chat-options"><label>Provider <select value={provider} onChange={(e) => setProvider(e.target.value as "CLAUDE" | "CODEX")}><option value="CLAUDE">Claude</option><option value="CODEX">OpenAI</option></select></label>
              <label>Pod context <select value={selectedPod} onChange={(e) => setSelectedPod(e.target.value)}><option value="">No Pod selected</option>{pods.map((pod) => <option key={pod.id} value={pod.id}>{pod.name}</option>)}</select></label>
            </div>
            <VoicePanel
              onSessionExpired={sessionExpired}
              onSpeakingChange={setVoiceSpeaking}
              onTranscript={recordVoiceTranscript}
            />
            <div className="messages" aria-live="polite">
              {messages.map((message, i) => (
                <div key={i} className={"message " + message.role}>
                  {message.role === "assistant" ? <MoniqueChatAvatar /> : <span className="message-avatar user-message-avatar">You</span>}
                  <p>{message.content}</p>
                </div>
              ))}
              {chatPending && <div className="message assistant" aria-live="polite">
                <MoniqueChatAvatar />
                <p className="typing-indicator" aria-label="MONIQUE is thinking">
                  <span /><span /><span />
                </p>
              </div>}
            </div>
            <form className="composer" onSubmit={(e) => void sendChat(e)}>
              <textarea aria-label="Chat with MONIQUE" rows={3} placeholder="Ask MONIQUE to plan a safe server operation…" value={prompt} onChange={(e) => setPrompt(e.target.value)} maxLength={8000} />
              <div className="composer-footer"><span>No commands will be executed.</span><button className="primary" type="submit" disabled={busy || !prompt.trim()}>Send →</button></div>
            </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
