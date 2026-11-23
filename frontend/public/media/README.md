# MONIQUE frontend motion assets

This directory is intentionally reserved for the user's original **Monique typing** video, adapted for the website.

## Required files to add

- `monique-typing-wide.mp4` — 16:9, 1280 × 720 cinematic layout. Displays Monique typing at the right with a dim background for legible app content.
- `monique-typing.mp4` — 9:16, 540 × 960 portrait layout for mobile.

Both are silent H.264 MP4 loops (no audio), produced from the user's provided ten-second clip. They must be uploaded here for the motion background to play. The frontend is intentionally safe without them: it falls back to a dark blue-purple gradient, and the generated MONIQUE portrait is already embedded in `src/moniqueAvatar.ts`.

The two actual MP4 files are included in the handoff ZIP supplied with the pull request. Upload both MP4s to this folder on the same feature branch before merging the PR, then deploy the new commit using Coolify.

The frontend uses HTML `autoPlay`, `muted`, `loop`, and `playsInline`; it disables video for users requesting reduced motion. The background video remains decorative and never accepts pointer events.

Please verify the videos load from the final deployment at `/media/monique-typing-wide.mp4` and `/media/monique-typing.mp4` (HTTP 200), and check Web Console for video decoding errors.
