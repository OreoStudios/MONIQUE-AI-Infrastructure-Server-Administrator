# ADR 0003 — Engine-specific data services and broker lifecycle contracts

**Decision:** Accepted as architecture direction, implementation pending  
**Date:** 2026-11-14

## Context

MONIQUE must install and manage PostgreSQL, MySQL/MariaDB, Redis/Valkey, MongoDB and RabbitMQ/AMQP across independent Pods. Database data, cache persistence and broker message state have distinct recovery semantics. A generic root shell function or generic “backup everything” command cannot guarantee correctness.

## Decision

1. Separate tracked `DataServiceInstance` resources from Pods, connected provider accounts and installed panels.
2. Define distinct `dataservices` and `messaging` bounded contexts, with version-aware capabilities and worker-only execution.
3. Support `DOCKER_COMPOSE`, `SYSTEM_PACKAGE`, `EXISTING` and `MANAGED_SERVICE` modes, subject to preflight checks.
4. Require per-operation immutable plans, explicit write approval, verified resource scope, secret vault use and redacted audit evidence.
5. Treat database backups, Redis durability profiles, RabbitMQ definitions export and actual queued messages as **different assets**.
6. Require verified restore or continuity tests before migration cutover; preserve source through a documented recovery window.
7. Do **not** add Spring Data Redis/MongoDB, Spring AMQP or Spring Kafka as requirements merely because MONIQUE manages these external engines.
8. Catalog entries remain `ROADMAP_NOT_IMPLEMENTED` until release-gated sandbox tests prove individual capabilities.

## Rejected alternatives

Unrestricted model-driven root scripts; one generic DB/broker recovery workflow; assuming message broker definitions include queued messages; bundling every database into MONIQUE's own backend deployment.

## Consequences and validation

More adapter-specific work and compatibility tests, but safe lifecycle, privilege and recovery boundaries. Verify package/image signatures, credentials, listening addresses, backup/restore, queue payload continuity, port conflicts, resource exhaustion and no changes to unrelated Pterodactyl/Coolify services.

See [Data Services](../DATA-SERVICES.md), [Integration Architecture](../INTEGRATION-ARCHITECTURE.md).
