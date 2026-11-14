# ADR 0005 — Tauri desktop is a peer API client, not a duplicate executor

**Status:** Accepted as architecture direction; not implemented  
**Date:** 2026-11-14

## Context
Operators want a native Windows/macOS/Linux application that feels like Claude Code but includes conversational AI, Pod configuration, monitoring and workflow approvals. MONIQUE's Java backend already owns the infrastructure security model.

## Decision
- Build a **Tauri 2 / Rust + React** desktop application with chat, Pod management, jobs/approvals, observability and membership.
- Reuse typed backend APIs and React UI components; do not parse CLI stdout for critical state or reimplement authorization in Rust.
- Support **remote client mode first** (connect securely to a customer-hosted MONIQUE server); support **local sidecar mode** only after a verified Java runtime, database strategy and safe execution lifecycle exist.
- Rust may supervise a *specific trusted packaged sidecar* and OS integrations, but may not expose arbitrary shell, root SSH or secrets through Tauri webview commands.
- Apply least-privilege Tauri v2 capabilities, authenticated loopback/IPC, OS-protected tokens, signed installers and update safety.
- Keep subscription verification distinct from host operation approvals and provide recovery after expiry.

## Distribution experience requirement

The desktop product is **one installed application** even if its local runtime uses multiple processes. For supported local mode the Tauri installer must automatically provision the compatible Java runtime, Spring Boot Core, isolated PostgreSQL and restricted worker; no manual database setup or independent service installation is part of the normal customer path. Verify component readiness before launching membership and Pod onboarding.

The prior remote-client-first implementation order remains valid. A remote-only desktop client must be clearly labeled as such and must **not** claim fully local operation until the bundled persistence/worker lifecycle has been tested.

See [Installation & Onboarding](../INSTALLATION-AND-ONBOARDING.md).

## Trade-offs
Tauri offers lightweight cross-platform native windows and sidecar packaging, but coupling Java 21/ PostgreSQL and durable workers to desktop lifecycle adds installer complexity. Desktop users should be able to manage an always-on remote server instead of forcing a permanent local service.

## Acceptance
Build Windows/macOS/Linux, validate auth and API contracts, prove no shell bypass, test sidecar process crash/update, secure secrets, persistence and migration recovery.

See [Desktop Architecture](../DESKTOP-APP.md), [Self-hosting](../SELF-HOSTING-AND-CLI.md), [Tauri v2 official docs](https://v2.tauri.app/develop/sidecar/).
