export function microphoneEnabled(p: { muted: boolean; manual: boolean; pressed: boolean; talking: boolean; allowInterruptions: boolean }) {
  return !p.muted && (p.manual ? p.pressed : (!p.talking || p.allowInterruptions));
}

export function canStartWorkConversation(p: { enabled: boolean; muted: boolean; talking: boolean; responding: boolean;
  userSpeaking: boolean; pressed: boolean; now: number; lastActivity: number; jobsUpdatedAt: number }) {
  return p.enabled && !p.muted && !p.talking && !p.responding && !p.userSpeaking && !p.pressed
    && p.now - p.lastActivity >= 20000 && p.now - p.jobsUpdatedAt <= 10000;
}
