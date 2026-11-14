# Architecture Decision Records (ADRs)

Decisions are captured as append-only records. Each new decision must state its status, context, alternatives considered, consequences and links to implementation or validation evidence. Changing an accepted decision requires a superseding ADR.

| ADR | Status | Decision |
|---|---|---|
| [0001](0001-observability-security-stack.md) | Accepted as design direction; not implemented | Prometheus + Actuator/Micrometer as preferred metrics stack; optional Grafana and Wazuh |

| [0002](0002-provider-integration-model.md) | Accepted as design direction; not implemented | Provider-aware account/Pod model and capability-based adapters across 129 roadmap integrations |
| [0003](0003-data-services-and-brokers.md) | Accepted design / not implemented | Database, cache and message broker lifecycle boundaries |
| [0004](0004-self-hosted-cli-membership.md) | Accepted design direction; not implemented | Self-hosted CLI and monthly membership licensing |
| [0005](0005-tauri-desktop-client.md) | Accepted design direction; not implemented | Tauri Rust desktop with shared API and safe local backend sidecar |
