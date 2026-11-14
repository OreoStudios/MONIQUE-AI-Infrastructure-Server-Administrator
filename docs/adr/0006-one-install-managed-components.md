# ADR 0006 — One install, automatically managed MONIQUE components

**Status:** Accepted as target product requirement; implementation pending  
**Date:** 2026-11-14

## Context

MONIQUE is internally composed of a Spring Boot/Spring Modulith Core, PostgreSQL, restricted execution workers, and optional dashboard / CLI / Tauri desktop clients. A modular system should not force customers to manually install or configure all of its constituent services.

## Decision

1. **End-user promise:** one desktop installer or guided self-hosted server bootstrap, even if multiple processes/containers run internally.
2. **Local desktop:** Tauri/Rust supervises a verified Core/Java sidecar and automatically provisions isolated PostgreSQL, safe credential storage, and restricted workers as appropriate. Local packaging must be truly tested before this mode is advertised.
3. **Server:** a single supported bootstrap flow manages the pinned Core, PostgreSQL, worker and optional dashboard stack. Existing OS components and Docker runtime may require detection and explicitly approved provisioning, not undisclosed root actions.
4. **Onboarding after readiness:** sign in and activate membership, select AI provider/BYOK, add and verify first Pod, then run read-only diagnostics/chat. Customer Pod management never starts during installer bootstrap.
5. **Safety:** no default public database endpoint, no generic root shell exposure, no overwriting existing services, no deletion of data on automatic repair/uninstall, no interruption of critical jobs during updates.
6. **Quality gates:** install/repair/update/uninstall are idempotent, crash-recoverable and verified by end-to-end health checks and Windows/macOS/Linux/server tests.
7. **Status:** no working one-click installer or fully packaged local PostgreSQL runtime is delivered by this documentation.

## Alternatives

- Require users to install Java/PostgreSQL/workers manually: rejected as poor customer experience.
- Merge all internal components into one process: rejected as unnecessary coupling and weak security isolation.
- Silently use system root/administrator permissions to make installation "easy": rejected as unsafe.
- Claim Tauri bundling alone makes the database self-contained: rejected; durable state and schema migration remain significant engineering work.

## Consequences

More cross-platform installer, local PostgreSQL lifecycle, signing, update and crash-recovery engineering, in exchange for a simple customer experience. Internal service boundaries remain independently testable and upgradable.

See [Installation & Onboarding](../INSTALLATION-AND-ONBOARDING.md), [Desktop](../DESKTOP-APP.md), [Self-hosting](../SELF-HOSTING-AND-CLI.md).
