# MONIQUE React dashboard — implemented development preview

This frontend is now a real **React 19 / TypeScript / Vite** web application replacing the missing frontend directory referenced by the repository's existing compose.yaml.

## Start the full stack

From the repository root, configure .env with random MONIQUE_ADMIN_TOKEN (at least 32 characters), MONIQUE_ENCRYPTION_KEY_BASE64 (32-byte key), and POSTGRES_PASSWORD. Then:

~~~sh
docker compose up --build
~~~

Open http://127.0.0.1:8088. The Nginx frontend proxies /api to the Spring Boot backend on the private Compose network; PostgreSQL and Core are not published to public interfaces.

Enter the MONIQUE_ADMIN_TOKEN from .env into the login form. It is kept **in the browser tab's memory**, not persisted in localStorage/cookies. Current API authentication is a single-admin preview and is **not** paid account/OIDC membership authentication.

Features implemented:
- Control-plane status, execution-disabled mode and Pod inventory dashboard.
- Register a Pod with SSH credential, encrypted by MONIQUE's backend.
- List and remove local Pod inventory records. Removal never changes a remote server.
- Planning-only Claude/OpenAI chat, with optional selected Pod context.
- Generic API errors without showing provider secrets or SSH credentials.

All remote operations are disabled. Chat suggestions and Pod registration do NOT connect to SSH, verify host keys, deploy software or perform a migration.

## Local frontend development

~~~sh
cd frontend
npm install
npm run dev
npm run build
~~~

Vite proxies /api to http://127.0.0.1:8080 during development. Production is served through frontend/nginx.conf.

Current limitations: no persistent user accounts, paid membership checks, streaming chat, background jobs, real execution workers, or signed desktop installers. All are separate roadmap items.

## Cinematic MONIQUE background and avatar

The dashboard now supports the user's Monique AI typing clip as a full-viewport animated video background on login, overview, Pods, and Chat. The video loops with native HTML5 `loop`, is muted to allow autoplay, has a strong dark scrim over all screens for readability, and has a CSS gradient fallback. With reduced-motion preferences it is hidden.

To activate it, add two files to [public/media](public/media/README.md):

| Browser | File | Encoding |
|---|---|---|
| Desktop | `public/media/monique-typing-wide.mp4` | 1280×720 H.264 MP4, silent, `+faststart` |
| Mobile | `public/media/monique-typing.mp4` | 540×960 H.264 MP4, silent, `+faststart` |

The provided asset ZIP has both videos prepared from the original user-supplied video. Put both MP4s into `frontend/public/media/` in GitHub (upload to the feature branch) before merging/deploying for a fully animated background. Vite copies this folder to the built frontend root. Missing video files do not break authentication, API access, or navigation; they just remove the live background.

Assistant chat avatars use the generated portrait embedded at `src/moniqueAvatar.ts`; the animated three-dot response indicator appears while awaiting an AI reply. No SSH or backend execution capabilities are added by this visual-only change.


## MONIQUE live two-way voice (WebRTC)

The **AI Chat** page now includes a separate **Start voice conversation** control. It reuses the existing left-hand Monique video portrait: the normal typing video runs while idle, and the existing speaking-and-typing video is displayed when OpenAI reports that output audio is playing. This is an animation, **not word-accurate lip sync**.

The microphone is requested only after the operator clicks Start. Audio travels directly between the browser and OpenAI via WebRTC; the Spring Boot backend mints a 60-second ephemeral credential at the authenticated `POST /api/voice/session` endpoint. The permanent `OPENAI_API_KEY` is never sent to the browser and is not stored in localStorage. The frontend uses WebRTC audio for playback and Realtime data-channel transcript/audio-buffer events for the chat transcript and video state.

**Deployment:** set `OPENAI_API_KEY` as a runtime secret in Coolify. Optionally set `MONIQUE_VOICE_MODEL=gpt-realtime-2.1-mini` and `MONIQUE_VOICE_NAME=marin`. Your existing `MONIQUE_ADMIN_TOKEN` still protects session issuance. You don't need a new public port or a Coolify proxy. The web app must be served over HTTPS for microphone access; `frontend/nginx.conf` allows outbound requests to `https://api.openai.com` through CSP. The browser also needs outbound WebRTC connectivity to OpenAI. Keep your CloudPanel reverse proxy as-is.

**Privacy and billing:** speech is sent to OpenAI. Voice is opt-in, stops when Chat is left or the tab is hidden, and is capped by the browser UI at 15 minutes per activation. The backend limits token creation to 4 per minute and 20 per hour per running instance (single-admin development preview). Configure provider budget limits separately; these frontend limits are **not** a hard provider billing cap. Audio input transcription is an optional separately billed model configured in the session; turns are not persisted to PostgreSQL.

**Safety:** The voice session has no tools and cannot execute SSH commands or control Pods. It uses planning-only server instructions and a short reply length. Do not dictate server secrets, passwords or private keys. Keep this prototype behind additional access controls before storing real infrastructure credentials.

**Manual test:** load the HTTPS dashboard and sign in using the admin token. In AI Chat, click Start voice conversation, grant microphone permission, speak a question, and verify that Monique answers aloud, the avatar switches videos during output audio, a transcript appears in the chat, and the End/Mute controls work. Confirm that navigating away, hiding the tab, and disconnecting all terminate the microphone. If there is no provider key, the voice control reports a configuration error instead of exposing credentials.


## Persistent administrator sessions (Spring Security + Spring Data JPA)

MONIQUE now exchanges the administrator token at `POST /api/auth/login` for an opaque, random, **HttpOnly** cookie (`SameSite=Strict`, `Secure` on HTTPS, `Path=/api`). The admin token is no longer sent on every React request and is **never saved in browser storage**. The backend hashes the session ID before storing its record in PostgreSQL through Spring Data JPA. Session records persist across backend restarts and Coolify redeployments; the cookie has a maximum lifetime of 12 hours. Rotating `MONIQUE_ADMIN_TOKEN` invalidates older sessions automatically.

The frontend restores authentication using `GET /api/auth/session` and its CSRF value; it explicitly signs out with `POST /api/auth/logout`. All cookie-authenticated unsafe API requests carry a session-bound `X-MONIQUE-CSRF` header, including voice credential issuance. Legacy direct Bearer clients continue working. A true HTTP 401 returns the UI to the login form; API errors such as 502/503 do **not** falsely log the user out.

The new `admin_sessions` table is automatically created by the existing PostgreSQL `schema.sql` initializer on startup. Hibernate validates its JPA mapping without editing Pod or audit storage. No changes to CloudPanel/Nginx, external ports, Wings, or Pterodactyl are required. Keep `MONIQUE_WEB_ORIGIN=https://monique.oreostudios.fr` in Coolify so the session cookie is Secure.

**Manual verification**: log in, reload the page, enter AI Chat and send multiple messages; refresh again and confirm the dashboard remains authenticated. Restart only the MONIQUE backend and verify the session restores. Test logout, CSRF rejection, admin-token rotation, and automatic return to login on a 401. If the provider returns 502/503, an error appears inside the dashboard without suggesting the administrator session expired.
