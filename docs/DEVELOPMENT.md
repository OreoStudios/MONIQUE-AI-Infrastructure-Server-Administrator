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

