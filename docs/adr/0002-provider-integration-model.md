# ADR 0002 — Typed provider integrations instead of universal SSH

**Date:** 2026-11-14  
**Status:** Accepted design direction; implementation pending  
**Decision owners:** MONIQUE maintainers

## Context

Monique targets heterogeneous infrastructure: dedicated/VPS hosts with root access, panels installed on hosts, managed PaaS offerings (Scalingo, Railway, Render, Fly.io), cloud APIs (Hetzner, DigitalOcean), DNS and monitoring. A universal "pod + SSH password" model cannot represent them correctly. It would also encourage uncontrolled privileged shell execution and unsafe migrations.

## Decision

- Separate `Pod` (SSH-managed host), `ConnectedAccount` (external service account), `IntegrationInstallation` (panel/service deployed onto a Pod), and `RemoteResource` (service, VM, deployment, DNS record, database).
- Prefer documented least-privilege vendor APIs; allow restricted CLI/SSH workers only for explicitly scoped host-local operations where no suitable API exists.
- Maintain a versioned registry of capabilities and required scopes; all provider actions run via policy/approval/durable job workers, never directly from the AI provider.
- Implement foundational adapters before broad ecosystem reach; the catalog is a roadmap and does not indicate active support.
- Treat cross-provider migration as an independently verified workflow with backups and rollbacks, not a generic "copy the server" action.

## Consequences

**Benefits:** accurate model of managed platforms, testable capability boundaries, safer least-privilege integration, reusable workflows and clear unsupported-state messaging.

**Costs:** per-provider authentication, version compatibility, API schema changes, service-specific restore logic, integration test fixtures, quota/cost controls, and maintenance effort.

**Rejected alternatives:** raw LLM-generated root shell as the universal adapter; installing provider CLI tools in the public API container; treating managed PaaS accounts as Pods; declaring integrations "supported" when only a documentation link exists.

## Acceptance gates

Read-only discovery and auth checks first; audited, immutable previews; negative permissions tests; integration sandbox verification; release/status updates in both machine registry and human docs. No automatic destructive retries.

See [INTEGRATIONS.md](../INTEGRATIONS.md) and [INTEGRATION-ARCHITECTURE.md](../INTEGRATION-ARCHITECTURE.md).
