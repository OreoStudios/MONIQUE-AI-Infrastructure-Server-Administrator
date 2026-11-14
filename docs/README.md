# Engineering documentation

Document ID **MON-ARCH-001** · Revision **1.0** · Date **2026-11-14** · Status **proposed architecture / implementation in progress**.

## How to read this specification

1. [Architecture](ARCHITECTURE.md) establishes what the system does and does **not** trust.
2. [Spring stack](SPRING-STACK.md) identifies supported frameworks, starter artifacts and decisions.
3. [Spring Modulith](SPRING-MODULITH.md) specifies module contracts enforced by tests.
4. [Observability and Monitoring](OBSERVABILITY.md) defines Prometheus, Grafana, host exporters and signal freshness.
5. [Security Monitoring](SECURITY-MONITORING.md) defines the optional Wazuh integration and detection workflow.
6. [Security Model](SECURITY.md) defines the threat boundary and controls (including monitoring connectors).
7. [Development](DEVELOPMENT.md) explains the prototype constraints.
8. [Architectural Decision Records](adr/README.md) explain trade-offs.

Planned specifications not yet published: AI orchestration, durable execution/migrations, detailed API contracts and the full quality plan. References to those future documents are roadmap items, **not evidence of shipped functionality**.

### Language

- **Pod:** one registered host/SSH endpoint; not Kubernetes.
- **Control plane:** Spring Boot application, state, authz and approval policy.
- **Worker:** restricted process that executes a narrowly scoped, approved step.
- **Plan:** immutable, structured list of proposed operations, preconditions and postconditions.
- **Approval:** an authorization decision against a concrete plan hash and time-limited scope.
- **Adapter:** version-aware implementation for a specific API/SSH operation.
- **Current:** actually exists in the repository; **Target:** proposed; **Deferred:** not scheduled for MVP.

### Design requirements

R1 — An AI response must never by itself confer authorization to mutate a host.

R2 — Pod credentials may not be included in prompts, logs, exceptions, or API responses.

R3 — Infrastructure jobs must have a durable identity, immutable approved plans, auditable step outputs and verifiable outcomes.

R4 — A write operation involving protected services must fail closed until explicit authorization and impact checks pass.

R5 — Before migration, source data integrity, target capacity, port conflicts and rollback feasibility must be assessed.

R6 — Each module owns its domain and persists through its own repository/API; cross-module SQL and direct references to internal packages are forbidden.

R7 — Support IPv4/IPv6 and multiple addresses per Pod while distinguishing discovered routes from user-entered metadata.

R8 — The platform itself should not depend on the very host it is migrating.

R9 — Missing, stale or unavailable metrics cannot be interpreted as healthy; mandatory pre-cutover evidence must be fresh.

R10 — Wazuh detections and Prometheus alerts may inform an investigation but cannot directly grant write permission.

R11 — Monitoring and security backends must be optional, separately deployable, and usable through least-privilege integrations.

### Documentation maintenance

Keep ADRs append-only. A change in dependency direction, trust model or storage ownership requires updating the design, ADRs, contracts and tests in the same PR. Diagrams here are source-controlled Mermaid, not screenshots.
