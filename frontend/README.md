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
- Control-plane status, opt-in approved SSH execution and Pod inventory dashboard.
- Register a Pod with SSH credential, encrypted by MONIQUE's backend.
- List and remove local Pod inventory records. Removal never changes a remote server.
- Claude/OpenAI chat with command proposals for the selected pod.
- Generic API errors without showing provider secrets or SSH credentials.

Remote execution is disabled by default. When enabled, approved command jobs connect
over SSH with pinned host-key verification. Chat and pod registration alone never
execute commands. See the SSH administration section below for setup and limits.

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


## Conversation preferences and microphone behavior

Open **Settings** to save your preferred name, response language, microphone mode,
interruption preference and conversation-while-working preference. Settings are
stored in PostgreSQL for this single-admin installation. Text replies apply changes
immediately; restart an active voice session to apply saved settings. The name is
sent to the chosen AI provider as personalization, not identity verification.

Automatic language follows the latest utterance in voice and text, including
English, French, Spanish and other supported model languages. A fixed language can
also be selected. Dashboard labels are not translated. Language detection and
transcription depend on the model and microphone; confirm important technical terms.

Noisy-room mode uses server noise reduction, a stronger speech threshold and browser
echo cancellation/noise suppression with automatic gain disabled. Automatic speech
interruptions are off by default. During playback, the outgoing microphone is gated
unless interruptions are allowed. **Interrupt MONIQUE** deliberately clears playback.
For noisy rooms, **Push to talk** sends audio only while the button is held (pointer,
Space or Enter); release commits the turn. Canceling, losing focus or a very short
press discards it. This improves control but is not a guarantee of noise immunity.

With voice connected, an approved command RUNNING on the selected pod can trigger
one short friendly question after 20 seconds of conversation inactivity. This needs
fresh status from the backend and does not include shell output or credentials.
It never triggers during speech, playback, response generation or microphone/speaker
mute. **Quiet while working** disables it for the current session. Background SSH
work remains independent of voice; voice does not execute or approve commands.

**Owner-only speaker verification is not implemented.** The UI explicitly marks it
unavailable and the API rejects attempts to enable it. No voiceprint is collected.
A real implementation needs enrollment, a speaker-verification model and an audio
gate before provider submission; prompting Realtime to recognize a name is not an
authentication mechanism. Anyone audible to the active microphone may be heard.

Validation includes backend preference/session-payload tests and frontend microphone
gating/idle-conversation tests (`npm test`). Live acoustic behavior, accents and noise
levels still need a browser/microphone test after deployment.

## SSH administration with explicit command approval

Text chat now supports real SSH commands through both OpenAI and Claude. Set
`MONIQUE_EXECUTION_ENABLED=true` as a backend runtime variable in Coolify and rebuild
both services from this revision. The default remains disabled. Keep one backend
replica; do not redeploy while jobs are running. Voice remains advisory.

1. Select a registered pod in Chat. Its stored password or unencrypted private key
   is used only by the backend SSH client. The backend needs network access to the
   pod's SSH port. Commands run with the stored user's privileges; noninteractive
   `sudo -n` requires suitable sudo configuration. No password prompts or PTY are supported.
2. Open the SSH console under chat. From the VPS provider's trusted console, run
   `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub -E sha256` and save its SHA256
   fingerprint in MONIQUE. Use the public host key offered by the server. Unknown
   or changed keys fail closed before authentication. Never copy the private host key.
3. Ask MONIQUE to inspect OS, resources, ports and existing services before an install
   or hardening change. Chat creates a pending command card; it does not execute it.
4. Review the exact command, target, impact and deadline, then approve. All commands,
   including inspections and manually entered commands, require approval. Approvals
   expire after 30 minutes and cannot be replayed. One command can run per pod, with
   up to four workers overall. Commands and results are encrypted in PostgreSQL.
5. Inspect the actual exit code and console output. Output is capped at 64 KiB and
   appears after completion, not as a live terminal stream. The result button copies
   an excerpt into the chat draft: review/redact it before sending it to the provider.
   MONIQUE can then propose the next step and verification. Output is untrusted data.

The console supports general noninteractive administration, including installation
steps and audits; it is not a verified one-click Coolify/Pterodactyl installer or an
unattended agent. The operator must review generated commands. Installation plans
need current vendor instructions, compatible OS, domains/DNS, backups and conflict
checks. There is no automated rollback, file-transfer UI, interactive terminal, or
guarantee that an arbitrary command is safe. Avoid putting secrets in commands or output.

Timeout, disconnect or backend restart can leave a remote command running. Such jobs
are marked `UNKNOWN`, never automatically retried, and block further execution on
that pod until the operator inspects it through the provider console and acknowledges
the outcome. Acknowledgement does not terminate a process or undo changes. Do not
redeploy, rotate host trust, or change this application's own hosting while running
an install through it. Pod records with command history cannot be deleted through
the API; the history is retained for audit. Back up the database and encryption key.

Verification: backend tests include real SSH connections to a disposable in-process
server (exit status, host-key rejection before authentication, deadlines and output
limits) plus persistence/approval tests using H2 in PostgreSQL mode. Production
PostgreSQL schema and container startup are covered by the configured Compose CI
smoke job; run it before deployment. Live VPS installations require separate staging validation.

## Advanced OpenAI sysadmin reasoning and provider diagnostics

MONIQUE's OpenAI text chat defaults to **`gpt-6.2-astra`** with **`reasoning.effort=max`** via the Responses API. The output budget is 16,000 tokens including hidden reasoning tokens. With execution enabled, the model can propose SSH commands for approval using the workflow above. Otherwise it provides planning guidance only.

In **Coolify → MONIQUE AI → Environment Variables**, set the following **runtime** values:

```dotenv
OPENAI_API_KEY=<your private OpenAI API project key>
MONIQUE_CODEX_MODEL=gpt-6.2-astra
MONIQUE_CODEX_REASONING_EFFORT=max
MONIQUE_CODEX_MAX_OUTPUT_TOKENS=16000
```

Existing Coolify variables take precedence over `compose.yaml` defaults. **If MONIQUE_CODEX_MODEL still says `gpt-5.1-codex`, replace it explicitly**, even though the repository's default was upgraded. Never commit an API key into Git or send it in chat. API billing, API project model access, and rate limits are separate from a ChatGPT subscription.

The backend now returns a safe `{ "code": "OPENAI_...", "message": "..." }` for provider failures, and the React chat displays that message instead of the previous generic error. The response never forwards provider raw error bodies or API keys. Troubleshooting codes include `OPENAI_KEY_MISSING`, `OPENAI_KEY_REJECTED`, `OPENAI_MODEL_UNAVAILABLE`, `OPENAI_ACCESS_DENIED`, `OPENAI_BILLING_LIMIT`, `OPENAI_RATE_LIMIT`, and `OPENAI_OUTPUT_LIMIT`. Provider API errors must **not** be confused with MONIQUE admin sessions.

GPT-6.2 Astra is a premium model, and `max` reasoning may be slower and cost significantly more than a smaller model. The configured output budget is a per-request cap, not a guaranteed cost ceiling. OpenAI voice chat remains a separate Realtime model. The reverse proxy must allow long-running Responses API calls; the bundled Nginx increases the backend read timeout to 300 seconds, but an outer CloudPanel proxy may have its own timeout.

Test after redeploy: sign in, choose **OpenAI · Advanced Reasoning**, send a simple message, and inspect the displayed provider diagnostics if it fails. Validate actual access and billing at https://platform.openai.com/ rather than relying on a ChatGPT plan.
