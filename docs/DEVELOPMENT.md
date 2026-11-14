# Development — MONIQUE foundation

## Prerequisites
- Docker Engine + Docker Compose v2 for the integrated preview.
- Alternatively Java 21/Maven and Node.js 22 for local development.
- An isolated testing environment; **do not use production credentials yet**.

## Start a local preview

1. `cp .env.example .env`
2. Generate a fresh token: `openssl rand -hex 32` and set `MONIQUE_ADMIN_TOKEN`.
3. Generate a fresh AES key: `openssl rand -base64 32` and set `MONIQUE_ENCRYPTION_KEY_BASE64`.
4. Set a long random `POSTGRES_PASSWORD`.
5. Optional: set `ANTHROPIC_API_KEY` and/or `OPENAI_API_KEY` for planning-only chat.
6. `docker compose up --build`
7. Open **http://localhost:8088**, enter your admin token in the connection dialog.

Compose binds the frontend on localhost only. It does not publish PostgreSQL or the backend directly. The frontend reverse proxy forwards `/api/` to the backend container.

### Backend locally
With PostgreSQL running and environment variables configured:
```bash
cd backend
mvn test
mvn spring-boot:run
```

### Frontend locally
```bash
cd frontend
npm install
npm run dev
```
Vite proxies `/api/` to `http://localhost:8080`.

## Available API

All routes require `Authorization: Bearer <MONIQUE_ADMIN_TOKEN>`.

| Method | Route | Purpose |
|---|---|---|
| GET | `/api/status` | Version + execution disabled flag |
| GET | `/api/pods` | List sanitized pod inventory |
| GET | `/api/pods/{uuid}` | Get one pod |
| POST | `/api/pods` | Store encrypted pod credentials, IPs and metadata |
| DELETE | `/api/pods/{uuid}` | Remove database inventory **only**, not remote servers |
| GET | `/api/audit` | Recent inventory audit events |
| POST | `/api/ai/chat` | Claude/Codex planning-only completion |

Example pod registration:
```json
{
  "name": "Pod 1",
  "sshHost": "192.0.2.10",
  "sshPort": 22,
  "sshUsername": "root",
  "credentialType": "PRIVATE_KEY",
  "credential": "-----BEGIN OPENSSH PRIVATE KEY-----...",
  "addresses": [
    {"address": "192.0.2.10", "role": "PRIMARY"},
    {"address": "192.0.2.11", "role": "ALLOCATION"},
    {"address": "2001:db8::11", "role": "PUBLIC"}
  ]
}
```

Model access depends on your provider account and selected model name; override model environment variables if unavailable.

## Verification

Backend unit tests cover authenticated credential encryption and IP validation. Build and test commands are provided, but CI success must be checked in GitHub Actions; a committed implementation is not proof that it has been deployed.

## Distribution requirement: automatic dependencies, not developer setup (planned)

The **development preview** documented above currently requires Docker Compose (or Java + PostgreSQL for manual backend development). That does **not** define the supported end-user installation experience.

For a production release, users should download one signed Tauri installer or run one guided VPS bootstrap. The installer is responsible for provisioning the compatible Java runtime, Spring Core, PostgreSQL, restricted worker, secrets and private network endpoints. The final setup wizard handles Oreo Studios account/membership, AI provider selection and Pod enrollment.

**Release must not be labeled one-click until** clean OS installs, health checks, database backup/restart, worker lifecycle, failure recovery and safe upgrades all pass. Admin/root elevation must be explicit and narrowly scoped; no silent Docker installation or destructive database overwrite. Remote Desktop/CLI clients connecting to a server need no local Core or database.

Full requirements: [Installation & Onboarding](INSTALLATION-AND-ONBOARDING.md).

## Planned CLI + desktop distribution (NOT implemented)

The existing commands earlier in this document describe a **development preview**, not a working public installer. The proposed user-facing distribution will offer:

- **MONIQUE CLI** on PowerShell, bash and zsh; interactive Pod enrollment, login, chat and approval reviews.
- **MONIQUE Desktop** built with Tauri 2 / Rust + React; Pod dashboard, AI chat, infrastructure views, jobs, monitoring and account/membership.
- **MONIQUE self-hosted server** for 24/7 work; the CLI and desktop can connect remotely over authenticated HTTPS.
- **Local desktop mode** through a restricted Java sidecar, after solving cross-platform Java runtime + PostgreSQL packaging and lifecycle.

Illustrative **future** commands, not runnable in the current repository:

```powershell
monique login
monique license status
monique pod add
monique pod list
monique chat
monique desktop
monique connect --url https://monique.example.net
```

**Build gate:** no Windows installer, macOS application, Linux AppImage/deb/rpm, CLI executable or licensing system is released by this documentation change. The present frontend/server build must be separately validated before packaging. See [Desktop](DESKTOP-APP.md) and [Self-hosting & CLI](SELF-HOSTING-AND-CLI.md).

## Planned production obfuscation stage

Unobfuscated developer builds remain available for debugging. A future, **release-only** CI job will version-pin the OreoObfuscator CLI, transform compiled MONIQUE-owned Java classes with Spring-compatible keep rules, repackage the executable JAR, rerun startup/security/Modulith tests on Java 21, then sign the final artifact. The obfuscator's JDK 25 tooling and private Maven license-contract credentials belong to CI, not the customer's installer.

The actual invocation/configuration must be derived from the pinned obfuscator's supported options; **no CLI command or CI workflow has been implemented yet**. `mapping.json` and private CI credentials must never be published.

See [Release Protection](RELEASE-PROTECTION.md).
