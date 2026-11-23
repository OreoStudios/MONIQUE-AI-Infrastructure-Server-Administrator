import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiRequestError, AuthenticationRequiredError } from "./api";

type VoiceState = "idle" | "connecting" | "connected";
type VoiceRole = "assistant" | "user";

type LiveEvent = {
  type?: string;
  transcript?: string;
  delta?: string;
  item_id?: string;
};

type LiveResources = {
  abort: AbortController;
  stream?: MediaStream;
  peer?: RTCPeerConnection;
  channel?: RTCDataChannel;
  audio?: HTMLAudioElement;
  timeout?: number;
  seenTranscripts: Set<string>;
};

type VoicePanelProps = {
  onSessionExpired: () => void;
  onSpeakingChange: (speaking: boolean) => void;
  onTranscript: (role: VoiceRole, transcript: string) => void;
};

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
export default function VoicePanel({ onSessionExpired, onSpeakingChange, onTranscript }: VoicePanelProps) {
  const [state, setState] = useState<VoiceState>("idle");
  const [microphoneMuted, setMicrophoneMuted] = useState(false);
  const [speakerMuted, setSpeakerMuted] = useState(false);
  const [error, setError] = useState("");
  const [caption, setCaption] = useState("");
  const resourcesRef = useRef<LiveResources | null>(null);
  const mountedRef = useRef(true);
  const speakerMutedRef = useRef(false);

  const stop = useCallback(() => {
    const current = resourcesRef.current;
    resourcesRef.current = null;
    if (current) {
      current.abort.abort();
      if (current.timeout !== undefined) window.clearTimeout(current.timeout);
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
    };
    resourcesRef.current = resources;
    setState("connecting");
    setError("");
    setCaption("");

    try {
      // Ask for the microphone after an explicit user click, never automatically.
      const mic = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      });
      resources.stream = mic;
      if (resourcesRef.current !== resources) { mic.getTracks().forEach(t => t.stop()); return; }

      let data: { value?: unknown };
      try {
        data = await api<{ value: string }>("/voice/session", {
          method: "POST",
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
      channel.onopen = () => {
        if (resourcesRef.current === resources) {
          setState("connected");
          setError("");
        }
      };
      channel.onmessage = (message) => {
        if (resourcesRef.current !== resources) return;
        let event: LiveEvent;
        try { event = JSON.parse(String(message.data)) as LiveEvent; }
        catch { return; }
        switch (event.type) {
          case "output_audio_buffer.started":
            onSpeakingChange(true);
            break;
          case "output_audio_buffer.stopped":
          case "output_audio_buffer.cleared":
          case "input_audio_buffer.speech_started":
            onSpeakingChange(false);
            if (event.type === "input_audio_buffer.speech_started") setCaption("");
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
  }, [onSessionExpired, onTranscript, onSpeakingChange, stop]);

  function toggleMic() {
    const current = resourcesRef.current;
    if (!current?.stream) return;
    const next = !microphoneMuted;
    current.stream.getAudioTracks().forEach(track => { track.enabled = !next; });
    setMicrophoneMuted(next);
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
        <div><strong>Live voice with MONIQUE</strong><span>Two-way OpenAI Realtime conversation · planning only</span></div>
        <span className={"voice-status " + state} aria-live="polite">
          {state === "idle" ? "Offline" : state === "connecting" ? "Connecting…" : "Listening"}
        </span>
      </div>
      <div className="voice-buttons">
        {state === "idle"
          ? <button type="button" className="primary" onClick={() => void start()}>🎙 Start voice conversation</button>
          : <button type="button" className="secondary" onClick={stop}>■ End voice conversation</button>}
        {state === "connected" && <>
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
      <p className="voice-privacy">Microphone access requires your permission and stops when you leave Chat, hide this tab, or end the session. Voice is sent to OpenAI. No server commands are executed.</p>
    </section>
  );
}
