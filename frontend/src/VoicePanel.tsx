import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiRequestError, AuthenticationRequiredError, type AssistantPreferences, type CommandJob } from "./api";
import { microphoneEnabled, canStartWorkConversation } from "./voicePolicy";

type VoiceState = "idle" | "connecting" | "connected";
type VoiceRole = "assistant" | "user";

type LiveEvent = {
  type?: string;
  transcript?: string;
  delta?: string;
  item_id?: string;
  item?: { type?: string; name?: string; call_id?: string; arguments?: string };
};

type LiveResources = {
  abort: AbortController;
  stream?: MediaStream;
  peer?: RTCPeerConnection;
  channel?: RTCDataChannel;
  audio?: HTMLAudioElement;
  timeout?: number;
  seenTranscripts: Set<string>;
  preferences?: AssistantPreferences;
  muted: boolean;
  talking: boolean;
  responseActive: boolean;
  userSpeaking: boolean;
  pressedAt: number;
  lastActivity: number;
  conversationTimer?: number;
  greetedJobs: Set<string>;
  handledCalls: Set<string>;
  lastProposedJobId?: string;
};

type VoicePanelProps = {
  onSessionExpired: () => void;
  onSpeakingChange: (speaking: boolean) => void;
  onTranscript: (role: VoiceRole, transcript: string) => void;
  jobs: CommandJob[];
  jobsUpdatedAt: number;
  podId: string;
  executionEnabled: boolean;
};

function syncMicrophone(current: LiveResources) {
  const manual = current.preferences?.noiseMode === "push_to_talk";
  const enabled = microphoneEnabled({ muted: current.muted, manual, pressed: current.pressedAt > 0,
    talking: current.talking, allowInterruptions: Boolean(current.preferences?.allowInterruptions) });
  current.stream?.getAudioTracks().forEach(track => { track.enabled = Boolean(enabled); });
}

function friendlyError(error: unknown): string {
  if (error instanceof DOMException && error.name === "NotAllowedError") {
    return "Microphone permission was denied. Allow microphone access to start voice chat.";
  }
  if (error instanceof DOMException && error.name === "NotFoundError") {
    return "No microphone was found on this device.";
  }
  if (error instanceof Error) return error.message;
  return "Could not connect to OpenAI Realtime voice.";
}

/**
 * Full-duplex audio over WebRTC. A short-lived credential is obtained from
 * MONIQUE's authenticated backend; the real OPENAI_API_KEY stays server-side.
 * Microphone access starts only on the user's button click.
 */
export default function VoicePanel({ onSessionExpired, onSpeakingChange, onTranscript, jobs, jobsUpdatedAt, podId, executionEnabled }: VoicePanelProps) {
  const [state, setState] = useState<VoiceState>("idle");
  const [microphoneMuted, setMicrophoneMuted] = useState(false);
  const [speakerMuted, setSpeakerMuted] = useState(false);
  const [error, setError] = useState("");
  const [caption, setCaption] = useState("");
  const [manual, setManual] = useState(false);
  const [holding, setHolding] = useState(false);
  const [workConversation, setWorkConversation] = useState(false);
  const [voiceExecution, setVoiceExecution] = useState(false);
  const jobsRef = useRef(jobs);
  const jobsUpdatedRef = useRef(jobsUpdatedAt);
  useEffect(() => { jobsRef.current = jobs; jobsUpdatedRef.current = jobsUpdatedAt; }, [jobs, jobsUpdatedAt]);
  const resourcesRef = useRef<LiveResources | null>(null);
  const mountedRef = useRef(true);
  const speakerMutedRef = useRef(false);

  const stop = useCallback(() => {
    const current = resourcesRef.current;
    resourcesRef.current = null;
    if (current) {
      current.abort.abort();
      if (current.timeout !== undefined) window.clearTimeout(current.timeout);
      if (current.conversationTimer !== undefined) window.clearInterval(current.conversationTimer);
      if (current.channel) {
        current.channel.onmessage = null;
        current.channel.onopen = null;
        current.channel.close();
      }
      if (current.peer) {
        current.peer.ontrack = null;
        current.peer.onconnectionstatechange = null;
        current.peer.close();
      }
      current.stream?.getTracks().forEach((track) => track.stop());
      if (current.audio) {
        current.audio.pause();
        current.audio.srcObject = null;
        current.audio.remove();
      }
    }
    onSpeakingChange(false);
    if (mountedRef.current) {
      setState("idle");
      setMicrophoneMuted(false);
      setCaption("");
      setHolding(false);
    }
  }, [onSpeakingChange]);

  useEffect(() => {
    mountedRef.current = true;
    const hide = () => { if (document.hidden) stop(); };
    const unload = () => stop();
    document.addEventListener("visibilitychange", hide);
    window.addEventListener("pagehide", unload);
    return () => {
      document.removeEventListener("visibilitychange", hide);
      window.removeEventListener("pagehide", unload);
      mountedRef.current = false;
      stop();
    };
  }, [stop]);

  const start = useCallback(async () => {
    if (resourcesRef.current) return;
    if (!navigator.mediaDevices?.getUserMedia || typeof RTCPeerConnection === "undefined" || !window.isSecureContext) {
      setError("Voice chat requires a supported browser on HTTPS with microphone access.");
      return;
    }

    const resources: LiveResources = {
      abort: new AbortController(),
      seenTranscripts: new Set(),
      muted: false, talking: false, responseActive: false, userSpeaking: false,
      pressedAt: 0, lastActivity: Date.now(), greetedJobs: new Set(), handledCalls: new Set(),
    };
    resourcesRef.current = resources;
    setState("connecting");
    setError("");
    setCaption("");

    try {
      // Ask for the microphone after an explicit user click, never automatically.
      const mic = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: false },
      });
      resources.stream = mic;
      if (resourcesRef.current !== resources) { mic.getTracks().forEach(t => t.stop()); return; }

      let data: { value?: unknown; preferences: AssistantPreferences; execution?: boolean };
      try {
        data = await api<{ value: string; preferences: AssistantPreferences; execution?: boolean }>("/voice/session", {
          method: "POST",
          body: JSON.stringify({ podId: podId || null }),
          signal: resources.abort.signal,
        });
      } catch (err) {
        if (err instanceof ApiRequestError && err.status === 429) {
          throw new Error("Voice session limit reached. Try again later.");
        }
        if (err instanceof ApiRequestError && err.status === 503) {
          throw new Error("Configure OPENAI_API_KEY in Coolify to enable live voice.");
        }
        throw err;
      }
      const ephemeralKey = data.value;
      if (typeof ephemeralKey !== "string" || !ephemeralKey.startsWith("ek_")) {
        throw new Error("Invalid short-lived voice credential.");
      }
      if (resourcesRef.current !== resources) return;
      resources.preferences = data.preferences;
      setManual(data.preferences.noiseMode === "push_to_talk");
      setWorkConversation(data.preferences.workConversation);
      setVoiceExecution(Boolean(data.execution));
      syncMicrophone(resources);

      const peer = new RTCPeerConnection();
      resources.peer = peer;
      const remoteAudio = document.createElement("audio");
      remoteAudio.autoplay = true;
      remoteAudio.setAttribute("playsinline", "");
      remoteAudio.muted = speakerMutedRef.current;
      resources.audio = remoteAudio;
      peer.ontrack = (event) => {
        if (resourcesRef.current !== resources) return;
        remoteAudio.srcObject = event.streams[0] || new MediaStream([event.track]);
        void remoteAudio.play().catch(() => setError("Tap the speaker control to allow audio playback."));
      };
      mic.getAudioTracks().forEach(track => peer.addTrack(track, mic));

      const channel = peer.createDataChannel("oai-events");
      resources.channel = channel;

      // A voice run_command call is routed to MONIQUE's authenticated backend, which
      // proposes, approves and runs the command over SSH, then polls for the real
      // result. The engine still enforces host-fingerprint pinning and immutable audit;
      // the operator granted standing authorization by selecting this pod.
      const settled = (status: string) => !["PENDING", "RUNNING"].includes(status);
      const awaitResult = async (jobId: string, timeoutSeconds: number): Promise<CommandJob | null> => {
        const deadline = Date.now() + Math.min(timeoutSeconds + 20, 1820) * 1000;
        while (Date.now() < deadline) {
          await new Promise(r => setTimeout(r, 1500));
          if (resourcesRef.current !== resources) return null;
          const list = await api<CommandJob[]>("/execution/pods/" + encodeURIComponent(podId) + "/jobs");
          const job = list.find(j => j.id === jobId);
          if (job && settled(job.status)) return job;
        }
        return null;
      };
      const runTool = async (item: NonNullable<LiveEvent["item"]>) => {
        const callId = item.call_id;
        if (!callId || item.type !== "function_call" || resources.handledCalls.has(callId)) return;
        resources.handledCalls.add(callId);
        let output: Record<string, unknown>;
        try {
          if (item.name !== "run_command") { output = { error: "Unknown tool." }; }
          else if (!podId) { output = { error: "No pod is selected, so no command can run." }; }
          else {
            const args = JSON.parse(item.arguments || "{}") as Record<string, unknown>;
            const timeoutSeconds = typeof args.timeout_seconds === "number" ? args.timeout_seconds : 300;
            const proposed = await api<CommandJob>("/execution/jobs", {
              method: "POST",
              body: JSON.stringify({ podId, command: args.command, purpose: args.purpose, timeoutSeconds }),
            });
            resources.lastProposedJobId = proposed.id;
            await api<CommandJob>("/execution/jobs/" + encodeURIComponent(proposed.id) + "/approve", { method: "POST" });
            const done = await awaitResult(proposed.id, timeoutSeconds);
            if (resourcesRef.current !== resources) return;
            output = done
              ? { status: done.status, exit_code: done.exitCode, job_id: done.id,
                  output: (done.output || "[No output]").slice(0, 6000) }
              : { status: "RUNNING", job_id: proposed.id,
                  message: "Still running past the wait window. Do not claim success; tell the operator it is still in progress." };
          }
        } catch (err) {
          if (err instanceof AuthenticationRequiredError) { stop(); onSessionExpired(); return; }
          output = { error: err instanceof Error ? err.message : "The command request failed." };
        }
        if (resourcesRef.current !== resources || channel.readyState !== "open") return;
        channel.send(JSON.stringify({ type: "conversation.item.create",
          item: { type: "function_call_output", call_id: callId, output: JSON.stringify(output) } }));
        channel.send(JSON.stringify({ type: "response.create" }));
        resources.lastActivity = Date.now();
        resources.responseActive = true;
      };

      channel.onopen = () => {
        if (resourcesRef.current === resources) {
          setState("connected");
          setError("");
          resources.conversationTimer = window.setInterval(() => {
            if (resourcesRef.current !== resources || channel.readyState !== "open" || !canStartWorkConversation({
              enabled: Boolean(resources.preferences?.workConversation), muted: resources.muted || speakerMutedRef.current,
              talking: resources.talking, responding: resources.responseActive, userSpeaking: resources.userSpeaking,
              pressed: resources.pressedAt > 0, now: Date.now(), lastActivity: resources.lastActivity, jobsUpdatedAt: jobsUpdatedRef.current,
            })) return;
            const running = jobsRef.current.find(job => job.status === "RUNNING" && !resources.greetedJobs.has(job.id));
            if (!running) return;
            resources.greetedJobs.add(running.id);
            resources.lastActivity = Date.now();
            resources.responseActive = true;
            // Only a confirmed RUNNING state is supplied, never shell output or invented progress.
            channel.send(JSON.stringify({ type: "conversation.item.create", item: {
              type: "message", role: "user", content: [{ type: "input_text", text:
                "App status: an approved SSH command is currently running. If I have not asked for quiet or focus, ask me one brief friendly question while we wait, using my conversation language and preferred name. Do not claim the command or installation succeeded. If I asked for quiet, remain silent." }],
            } }));
            channel.send(JSON.stringify({ type: "response.create" }));
          }, 5000);
        }
      };
      channel.onmessage = (message) => {
        if (resourcesRef.current !== resources) return;
        let event: LiveEvent;
        try { event = JSON.parse(String(message.data)) as LiveEvent; }
        catch { return; }
        switch (event.type) {
          case "output_audio_buffer.started":
            resources.talking = true;
            resources.lastActivity = Date.now();
            syncMicrophone(resources);
            onSpeakingChange(true);
            break;
          case "output_audio_buffer.stopped":
          case "output_audio_buffer.cleared":
            resources.talking = false;
            resources.lastActivity = Date.now();
            syncMicrophone(resources);
            onSpeakingChange(false);
            break;
          case "input_audio_buffer.speech_started":
            resources.userSpeaking = true;
            resources.lastActivity = Date.now();
            if (resources.preferences?.allowInterruptions) { onSpeakingChange(false); setCaption(""); }
            break;
          case "input_audio_buffer.speech_stopped":
            resources.userSpeaking = false;
            resources.lastActivity = Date.now();
            break;
          case "response.created":
            resources.responseActive = true;
            break;
          case "response.done":
            resources.responseActive = false;
            resources.lastActivity = Date.now();
            break;
          case "response.output_item.done":
            if (event.item?.type === "function_call") void runTool(event.item);
            break;
          case "response.output_audio_transcript.delta":
            if (typeof event.delta === "string") setCaption(previous => (previous + event.delta).slice(-1000));
            break;
          case "response.output_audio_transcript.done":
            if (typeof event.transcript === "string" && event.transcript.trim()) {
              const id = "a:" + (event.item_id || event.transcript);
              if (!resources.seenTranscripts.has(id)) {
                resources.seenTranscripts.add(id);
                onTranscript("assistant", event.transcript.trim());
              }
            }
            setCaption("");
            break;
          case "conversation.item.input_audio_transcription.completed":
            if (typeof event.transcript === "string" && event.transcript.trim()) {
              const id = "u:" + (event.item_id || event.transcript);
              if (!resources.seenTranscripts.has(id)) {
                resources.seenTranscripts.add(id);
                onTranscript("user", event.transcript.trim());
              }
            }
            break;
          case "error":
            resources.responseActive = false;
            setError("OpenAI reported a voice session error. Please restart voice chat.");
            break;
        }
      };

      peer.onconnectionstatechange = () => {
        if (resourcesRef.current !== resources) return;
        if (peer.connectionState === "failed" || peer.connectionState === "closed") {
          setError("Voice connection was lost.");
          stop();
        }
      };

      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      if (resourcesRef.current !== resources) return;
      const sdp = peer.localDescription?.sdp;
      if (!sdp) throw new Error("Could not prepare the microphone connection.");

      const answer = await fetch("https://api.openai.com/v1/realtime/calls", {
        method: "POST",
        body: sdp,
        headers: {
          Authorization: "Bearer " + ephemeralKey,
          "Content-Type": "application/sdp",
        },
        signal: resources.abort.signal,
        redirect: "error",
      });
      if (!answer.ok) throw new Error("OpenAI Realtime connection failed (HTTP " + answer.status + ").");
      const remoteSdp = await answer.text();
      if (resourcesRef.current !== resources) return;
      await peer.setRemoteDescription({ type: "answer", sdp: remoteSdp });

      // Avoid inadvertently keeping a paid microphone session open indefinitely.
      resources.timeout = window.setTimeout(() => {
        if (resourcesRef.current === resources) {
          stop();
          setError("Voice session ended after 15 minutes. Start a new session to continue.");
        }
      }, 15 * 60 * 1000);
    } catch (err) {
      if (resourcesRef.current !== resources) return;
      if (err instanceof AuthenticationRequiredError) {
        stop();
        onSessionExpired();
      } else {
        const message = friendlyError(err);
        stop();
        setError(message);
      }
    }
  }, [onSessionExpired, onTranscript, onSpeakingChange, stop, podId]);

  function toggleMic() {
    const current = resourcesRef.current;
    if (!current?.stream) return;
    const next = !microphoneMuted;
    current.muted = next;
    current.pressedAt = 0;
    setHolding(false);
    syncMicrophone(current);
    setMicrophoneMuted(next);
  }

  function interrupt() {
    const current = resourcesRef.current;
    if (current?.channel?.readyState !== "open") return;
    if (current.responseActive) current.channel.send(JSON.stringify({ type: "response.cancel" }));
    current.channel.send(JSON.stringify({ type: "output_audio_buffer.clear" }));
    current.lastActivity = Date.now();
    setCaption("");
  }

  function beginTalk() {
    const current = resourcesRef.current;
    if (!manual || !current || current.muted || current.pressedAt || current.channel?.readyState !== "open") return;
    interrupt();
    current.channel.send(JSON.stringify({ type: "input_audio_buffer.clear" }));
    current.pressedAt = Date.now();
    current.lastActivity = Date.now();
    syncMicrophone(current);
    setHolding(true);
  }
  function endTalk(send = true) {
    const current = resourcesRef.current;
    if (!current?.pressedAt) return;
    const duration = Date.now() - current.pressedAt;
    current.pressedAt = 0;
    current.lastActivity = Date.now();
    syncMicrophone(current);
    setHolding(false);
    if (current.channel?.readyState !== "open") return;
    if (!send || duration < 350) { current.channel.send(JSON.stringify({ type: "input_audio_buffer.clear" })); return; }
    current.channel.send(JSON.stringify({ type: "input_audio_buffer.commit" }));
    current.channel.send(JSON.stringify({ type: "response.create" }));
    current.responseActive = true;
  }

  function toggleSpeaker() {
    const next = !speakerMuted;
    speakerMutedRef.current = next;
    if (resourcesRef.current?.audio) resourcesRef.current.audio.muted = next;
    setSpeakerMuted(next);
    if (!next && resourcesRef.current?.audio) {
      void resourcesRef.current.audio.play().catch(() => setError("Browser blocked audio playback. Check site permissions."));
    }
  }

  return (
    <section className="voice-panel" aria-label="Live voice conversation">
      <div className="voice-heading">
        <div><strong>Live voice with MONIQUE</strong><span>Two-way OpenAI Realtime conversation · {voiceExecution ? "spoken SSH execution armed" : !executionEnabled ? "planning only" : podId ? "start voice to operate the selected pod" : "select one pod to arm SSH"}</span></div>
        <span className={"voice-status " + state} aria-live="polite">
          {state === "idle" ? "Offline" : state === "connecting" ? "Connecting…" : microphoneMuted ? "Microphone muted" : manual ? (holding ? "Listening" : "Push to talk") : "Connected"}
        </span>
      </div>
      <div className="voice-buttons">
        {state === "idle"
          ? <button type="button" className="primary" onClick={() => void start()}>🎙 Start voice conversation</button>
          : <button type="button" className="secondary" onClick={stop}>■ End voice conversation</button>}
        {state === "connected" && <>
          <button type="button" className="secondary" aria-pressed={!workConversation} onClick={() => {
            const current = resourcesRef.current;
            if (current?.preferences) current.preferences.workConversation = !workConversation;
            setWorkConversation(!workConversation);
          }}>{workConversation ? "Quiet while working" : "Chat while working"}</button>
          <button type="button" className="secondary" onClick={interrupt}>Interrupt MONIQUE</button>
          {manual && <button type="button" className="primary" disabled={microphoneMuted} aria-pressed={holding}
            onPointerDown={e => { e.currentTarget.setPointerCapture(e.pointerId); beginTalk(); }}
            onPointerUp={() => endTalk()} onPointerCancel={() => endTalk(false)} onLostPointerCapture={() => endTalk(false)}
            onKeyDown={e => { if ((e.key === " " || e.key === "Enter") && !e.repeat) { e.preventDefault(); beginTalk(); } }}
            onKeyUp={e => { if (e.key === " " || e.key === "Enter") { e.preventDefault(); endTalk(); } }}
            onBlur={() => endTalk(false)}>{holding ? "Listening — release to send" : "Hold to talk"}</button>}
          <button type="button" className="secondary" onClick={toggleMic} aria-pressed={microphoneMuted}>
            {microphoneMuted ? "Unmute microphone" : "Mute microphone"}
          </button>
          <button type="button" className="secondary" onClick={toggleSpeaker} aria-pressed={speakerMuted}>
            {speakerMuted ? "Enable speaker" : "Mute speaker"}
          </button>
        </>}
      </div>
      {caption && <p className="voice-caption" aria-live="polite">{caption}</p>}
      {error && <p className="voice-error" role="alert">{error}</p>}
      <p className="voice-privacy">Microphone access requires your permission and stops when you leave Chat, hide this tab, or end the session. Voice is sent to OpenAI. {voiceExecution ? "Spoken confirmation runs real SSH commands on the selected pod; the host fingerprint must be pinned first and every command is logged." : "No server commands are executed."}</p>
    </section>
  );
}
