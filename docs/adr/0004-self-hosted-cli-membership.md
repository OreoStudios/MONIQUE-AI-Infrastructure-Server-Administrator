# ADR 0004 — Self-hosted CLI-first MONIQUE with monthly membership

**Status:** Accepted as design direction; implementation pending  
**Date:** 2026-11-14

## Context
Operators want Claude Code–like interactivity while retaining control of VPS credentials and infrastructure. The product also needs a sustainable monthly commercial model without forcing hosting through Oreo Studios.

## Decision
- Self-hosting is a core deployment model: local CLI or customer-operated always-on server.
- CLI, desktop and browser are equal clients to a Spring API/policy/approval system; no client gets a model-to-root bypass.
- A monthly MONIQUE membership is planned for premium features with centrally verified, locally signed entitlements.
- Keep customer Pod credentials, job data and secret store locally/customer-hosted; Stripe billing and entitlement issuance remain central.
- BYOK supported provider API keys or a separately designed metered AI gateway; consumer chat subscriptions are not provider APIs.
- After expiry, disallow new premium work while preserving safe recovery/read-only/export; no in-flight migrations abandoned due to billing.
- Product pricing, legal licensing and offline grace duration are future decisions (seven-day grace is a design proposal only).

## Alternatives rejected
Cloud-only hosting, unrestricted CLI SSH tools, central collection of customer SSH passwords, terminating migrations on payment outage, and assuming public open-source code can always enforce paid-only use.

## Consequences
Cross-platform CLI packaging, secure login, offline license validation, quota accounting, durable jobs and legal license review require tests.

See [Self-hosting & CLI](../SELF-HOSTING-AND-CLI.md), [Licensing](../LICENSING-AND-BILLING.md).
