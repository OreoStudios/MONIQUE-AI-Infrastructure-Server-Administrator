# MONIQUE Security Model

## Implemented in the foundation

- Each API call requires a manually provided bearer token, configured via `MONIQUE_ADMIN_TOKEN` (32+ characters).
- The browser holds the token **in memory only**; it is not stored in cookies or localStorage.
- Pod SSH credentials are encrypted at rest with AES-256-GCM using random nonces and pod UUID as associated authenticated data.
- The AES key is injected through `MONIQUE_ENCRYPTION_KEY_BASE64`, not committed.
- Pod API responses never include credentials or ciphertext.
- Claude/Codex planning only receives pod names, IDs and IP counts; not keys, passwords, host address details or terminal output.
- No remote SSH sessions, shell commands, installs, migrations, or other server writes are implemented. AI chat always replies `executed: false`.
- SQL queries use bind parameters; infrastructure actions are not exposed through public HTTP endpoints.

## Not implemented — do not use in production yet

- Multi-user accounts, RBAC, MFA and token rotation; the initial token is single-operator.
- Host key verification, SSH/SFTP execution, scoped sudo, service adapters and approval gates.
- Signed plan fingerprints, immutable audit storage, backup verification and recovery.
- Job persistence, safe concurrency, idempotency, rate limits, AI cost controls and egress controls.
- Centralized secrets vault / KMS, encryption-key rotation, monitored audit retention.
- TLS termination, WAF, per-request quotas, hardened Content Security Policy and penetration tests.
- Schema migration engine (schema.sql is a development bootstrap only).

**Never expose the preview to the public Internet.** By default the Compose frontend binds only to `127.0.0.1:8088`. Use disposable hosts and fake credentials for development; do not enter real root passwords into an unreviewed build.

## Threat model

1. A prompt-injected SSH output tries to instruct Monique to run a destructive command: remote output is untrusted; future adapters need structured parsing and independent policy checks.
2. An LLM hallucinates completion: provider chat returns a plan only, with `executed: false`.
3. A compromised database reveals ciphertext: AES-GCM protects stored credentials; an attacker with both DB and encryption key can decrypt them, so keys must be isolated.
4. A leaked admin token grants access: store it off-device where possible, rotate on suspicion, and add RBAC/MFA before public deployment.
5. A migration could destroy unrelated services: safe-mode inspect, versioned plans, protected resources, verification and explicit approval must precede execution.

## Monitoring/security integrations — additional threat controls (planned)

**These measures are not implemented.** Prometheus and Wazuh evidence must be treated as sensitive, untrusted inputs that **cannot authorize action**.

| Risk | Control |
|---|---|
| Publicly exposed Node Exporter / Actuator endpoints | Private scrape network or authenticated reverse proxy, network ACLs; no Internet-wide access |
| Credential exposure through metrics/logs | Bounded label sets, log redaction, scoped secret references, no credentials in telemetry |
| SSRF through user/model-supplied Prometheus or Wazuh URLs | Admin-approved connector endpoints, DNS/IP egress policies, no arbitrary URLs in tool arguments |
| Excessive PromQL / indexer queries | Allowed query templates, bounded time ranges, query budgets and response-size limits |
| Injected commands in Wazuh alerts or remote logs | Treat all text as untrusted evidence; no prompt/system instruction escalation |
| False-positive security incident | Source/severity/freshness confidence, cross-source corroboration and manual review |
| Automatic active response causes outage | Wazuh active response / firewall lockdown **disabled by default**; explicit scoped change approval required |
| Data unavailable during risky migration | Fail closed on required verification; label telemetry stale/unavailable and require alternative verified plan |
| Monitoring stack becomes single point of failure | Isolated central services, independent control-plane heartbeat and tested restoration |

**Wazuh sizing:** a quickstart single-host central stack with server, indexer and dashboard for 1–25 agents recommends 4 vCPU, 8 GiB RAM and 50 GB (90-day horizon). Treat these as vendor guidance, not a guaranteed fixed requirement; investigate current vendor documentation before provisioning.

References: [Wazuh components](https://documentation.wazuh.com/current/getting-started/components/index.html) · [Wazuh sizing](https://documentation.wazuh.com/current/quickstart.html) · [Spring Boot actuator security](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html).

\n## Third-party connector threat model (planned)

- Treat provider URLs, webhooks, DNS targets, returned manifests and API responses as untrusted. Use explicit HTTPS endpoint allowlists, region scope, TLS pinning/verification, payload bounds, and network egress restrictions to prevent SSRF.
- Each connector stores credentials in the vault by **reference** and requires a verified tenancy, region, product version and explicit capability scopes. Managed PaaS credentials must not unlock SSH/root actions.
- Separate **inventory**, **configuration**, **deployment**, **migration**, and **destructive** capabilities; require approval for any write and elevated approval for deleting VMs, backups, domains, databases or firewall rules.
- Cloud APIs may incur charges; quota and price/cost acknowledgment are part of a proposed plan. No plan automatically purchases or provisions resources based only on AI text.
- Handle vendor rate limits and asynchronous accepted states. An HTTP 202 (or 200 with GraphQL errors) is not proof that a deployment or VM operation succeeded.
- No unreviewed fallback from failed provider API calls to root shell commands. Unsupported features must be displayed as **unsupported**, never silently faked.
- Separately verify DNS, public IP assignments, service continuity, persistence, backups and rollback when migrating across providers.

See [Integration Architecture](INTEGRATION-ARCHITECTURE.md) and [ADR 0002](adr/0002-provider-integration-model.md).\n