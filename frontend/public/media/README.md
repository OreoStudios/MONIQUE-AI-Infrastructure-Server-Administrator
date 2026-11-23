# MONIQUE frontend video assets

The UI uses two user-supplied clips. All video playback is muted, looped, inline and decorative; no AI response audio or speech synchronization is implied.

## Existing assets

- `monique-typing.mp4` — original Monique typing clip (portrait). Used as the full-screen **Overview** background and in the **AI Chat** portrait window while idle.
- `monique-typing-wide.mp4` — optional legacy desktop composite; no longer used in the interface.

## New required asset

- `monique-typing-speaking.mp4` — Monique typing and speaking, played while the AI chat request is pending. Derived from the user's uploaded `LOOKING AND TYPING AND SPEAKING.mp4`, optimized as a **540 × 960 H.264 MP4**, muted, ~10 seconds. Source footage is not altered otherwise.

This new binary must be uploaded into **this exact directory** on the PR branch before merging it. Source code alone does not include the new MP4. If it is missing, the chat panel falls back to the idle video instead of showing a broken element.

## Behavior

1. Login and Overview: full-bleed original typing footage behind the interface, dark readable overlay.
2. Pods: no background video.
3. Chat idle: original typing footage in the left-hand portrait panel, continuously looping.
4. Chat request in progress: show the new speaking+typing video, starting at 0 and looping if needed.
5. Request resolves or fails: return to idle typing footage.

On mobile, the portrait panel stacks above the chat. Users with `prefers-reduced-motion: reduce` do not see autoplaying footage.

Verify GET `/media/monique-typing.mp4` and GET `/media/monique-typing-speaking.mp4` return HTTP 200 on the deployed site after uploading, merging and redeploying via Coolify.
