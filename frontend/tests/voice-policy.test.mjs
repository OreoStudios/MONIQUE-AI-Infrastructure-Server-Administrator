import test from 'node:test';
import assert from 'node:assert/strict';
import { microphoneEnabled, canStartWorkConversation } from '../src/voicePolicy.ts';

test('background noise cannot interrupt playback with interruptions off', () => {
  const p = { muted: false, manual: false, pressed: false, talking: true, allowInterruptions: false };
  assert.equal(microphoneEnabled(p), false);
  assert.equal(microphoneEnabled({ ...p, talking: false }), true);
  assert.equal(microphoneEnabled({ ...p, allowInterruptions: true }), true);
  assert.equal(microphoneEnabled({ ...p, muted: true, allowInterruptions: true }), false);
});
test('push to talk sends audio only while held and never overrides mute', () => {
  const p = { muted: false, manual: true, pressed: false, talking: false, allowInterruptions: true };
  assert.equal(microphoneEnabled(p), false);
  assert.equal(microphoneEnabled({ ...p, pressed: true }), true);
  assert.equal(microphoneEnabled({ ...p, pressed: true, muted: true }), false);
});
test('work conversation needs fresh job evidence and an idle conversation', () => {
  const p = { enabled: true, muted: false, talking: false, responding: false, userSpeaking: false,
    pressed: false, now: 100000, lastActivity: 70000, jobsUpdatedAt: 98000 };
  assert.equal(canStartWorkConversation(p), true);
  for (const flag of ['muted', 'talking', 'responding', 'userSpeaking', 'pressed'])
    assert.equal(canStartWorkConversation({ ...p, [flag]: true }), false);
  assert.equal(canStartWorkConversation({ ...p, enabled: false }), false);
  assert.equal(canStartWorkConversation({ ...p, jobsUpdatedAt: 80000 }), false);
  assert.equal(canStartWorkConversation({ ...p, lastActivity: 95000 }), false);
});
