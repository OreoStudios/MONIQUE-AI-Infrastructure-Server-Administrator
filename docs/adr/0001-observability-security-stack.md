# ADR 0001 — Independent observability and optional security monitoring

**Status:** Accepted as architecture direction (implementation pending)  
**Date:** 2026-11-14  
**Decision owners:** MONIQUE maintainers  
**Scope:** Spring control plane, managed Pods, diagnostics and security evidence

## Context

MONIQUE must investigate server health, diagnose operational incidents and verify infrastructure migrations across multiple unmanaged Linux hosts. Reading the result of one SSH command is insufficient for historical diagnosis and cannot distinguish a healthy service from missing telemetry. MONIQUE also needs an optional security view without turning every deployed Pod into a heavyweight SIEM installation.

The control plane requires a monitoring strategy that supports small individual VPSs, existing monitoring investments and provider-neutral Java integration, while keeping **detection separate from authorization**.

## Decision

1. Use **Spring Boot Actuator + Micrometer** for first-party application instrumentation.
2. Prefer **Prometheus** for metrics storage, scraping and query via bounded read-only HTTP API.
3. Offer **Grafana** as optional dashboards/alerting; not a control-plane runtime dependency.
4. Permit **Node Exporter / approved exporters** per Pod, only through opt-in enrollment and secured private scraping.
5. Support **Wazuh as an optional security subsystem**: a centralized server/manager, indexer/dashboard as needed, and explicitly enrolled Pod agents. Prefer connecting existing installations.
6. Expose evidence through separate Spring Modulith **`observability`** and **`securitymonitoring`** bounded contexts.
7. Send only sanitized, timestamped, low-privilege evidence to Spring AI. Alert severity and metrics alone **never authorize** infrastructure modifications.
8. Report missing/stale data explicitly; block high-risk migration cutovers when their required checks cannot be verified.

## Alternatives considered

| Alternative | Outcome | Reason |
|---|---|---|
| SSH polling alone | Rejected as primary telemetry | No reliable history, high permission surface, hard to correlate |
| Grafana as the only backend | Rejected | Grafana is primarily visualization/alerting; actual metrics source remains necessary |
| Mandatory Wazuh on all Pods | Rejected | Central stack resource cost and operator complexity; not required for basic admin features |
| No built-in monitoring integration | Rejected | Cannot consistently verify jobs or correlate failures |
| All-in-one tool executing on security alerts | Rejected | Creates a dangerous detection-to-privileged-action escalation |
| Custom metrics database and SIEM | Rejected for MVP | High development/maintenance cost and reinvention of existing tools |

## Consequences

**Positive:** historical metrics, incident visibility, testable health gates, reusable external Prometheus/Grafana/Wazuh setups, independent subsystem failure handling, low-latency AI evidence enrichment.

**Costs:** credentials and network hardening for multiple connectors; data retention and storage operations; metric cardinality constraints; alert deduplication; Wazuh resource sizing and license obligations; additional test matrix.

**Risks:** mistaken correlations and false positives, time drift, stale scrape results, sensitive data in alert payloads, endpoint SSRF. Mitigation is bounded read-only APIs, defensive parsing, source timestamps, RBAC, independent verification, and operator approval.

## Implementation checkpoints

- Phase A: implement Actuator/Micrometer metrics in the Spring backend and verify locked-down scraping.
- Phase B: bounded Prometheus query adapter and low-cardinality service metrics; simulate stale data.
- Phase C: optional dashboard and exporter enrollment, plus independent control-plane uptime checks.
- Phase D: Wazuh manager and indexer read-only integration; normalized events and Pod/agent mapping.
- Phase E: cross-source incident correlation and migration pre/post-health verification with failure-injection tests.

**Not shipped yet:** No evidence that any of these services or module APIs are implemented or enabled by this ADR alone.

References: [Spring Boot metrics](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [Prometheus API](https://prometheus.io/docs/prometheus/latest/querying/api/), [Grafana](https://grafana.com/docs/grafana/latest/datasources/prometheus/), [Wazuh](https://documentation.wazuh.com/current/getting-started/components/index.html).
