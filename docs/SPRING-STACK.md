# Spring platform and library selection

**Baseline (2026-11-14)**

| Layer | Selected release / role |
|---|---|
| Runtime | Java 21 LTS |
| Core | **Spring Boot 4.1.2** (Spring Framework 7.1 generation) |
| Modularity | **Spring Modulith 2.1.1** |
| AI abstraction | **Spring AI 2.0.2** (supports Boot 4.0/4.1) |
| Persistence | PostgreSQL 17+, Spring JDBC initially; Spring Data JDBC may follow |
| HTTP frontend | React + TypeScript; Java Spring MVC backend |
| Deploy | Docker Compose for local development, hardened separated production deployment |
| Build | Maven; imported Spring AI and Modulith BOMs alongside Boot parent |

Versions use stable releases: **do not silently switch to Boot 4.2 / AI 2.1 release candidates**. Audit compatibility before changes.

## Spring dependencies: adopt, evaluate, defer

| Spring technology | Artifact / integration | Decision | Reason / scope |
|---|---|---|---|
| Spring Boot | `spring-boot-starter-webmvc` | **Now** | API routes, request validation, SSE |
| Spring Security | `spring-boot-starter-security` | **Now, harden later** | AuthN/AuthZ; migrate prototype single bearer secret to multi-user OAuth2/OIDC |
| Jakarta Validation | `spring-boot-starter-validation` | **Now** | DTO validation, data boundary |
| Spring JDBC | `spring-boot-starter-jdbc` | **Now** | SQL control and transactional persistence |
| Spring Transactions | auto-enabled by JDBC starter | **Now** | Atomic state updates + outbox |
| Spring Modulith core | `spring-modulith-starter-core` | **Now** | Module discovery and boundaries |
| Spring Modulith test | `spring-modulith-starter-test` (test) | **Now** | Module structure and slice tests |
| Spring Modulith docs | `spring-modulith-docs` (test) | **Next** | Generated module diagrams/canvases |
| Spring Modulith events JDBC | `spring-modulith-starter-jdbc` | **Next** | Reliable transactional event publication (not job queue) |
| Spring AI Anthropic | `spring-ai-starter-model-anthropic` | **Next** | Claude `ChatModel` / `ChatClient` |
| Spring AI OpenAI | `spring-ai-starter-model-openai` | **Next** | OpenAI chat-model orchestration |
| Spring AI tool APIs | `ChatClient`, structured/tool callbacks | **Next** | Read-only bounded tools; write-capable tools remain policy-gated |
| Spring AI MCP client | Spring AI MCP starter (confirm version and precise artifact before inclusion) | **Later** | MCP tool discovery with allowlist; not automatic blanket access |
| Spring Boot Actuator | `spring-boot-starter-actuator` | **Next — planned** | Health, readiness, metrics; restricted endpoints |
| Spring Observability | Micrometer + OpenTelemetry bridge | **Next** | Tracing, AI usage metrics and execution spans |
| Spring Boot Flyway | `spring-boot-starter-flyway` + DB-specific module where required | **Next** | Versioned, audited schema; replace `schema.sql` |
| Spring Data JDBC | `spring-boot-starter-data-jdbc` | **Evaluate** | Aggregate-focused persistence if JDBC boilerplate grows |
| Spring Data JPA | `spring-boot-starter-data-jpa` | **Defer** | Not required for controlled SQL-first domain model |
| Spring Cache | `spring-boot-starter-cache` | **Later** | Bounded inventory cache; never cache raw credentials |
| Spring Retry | `spring-retry` (or worker retries) | **Later** | Retry policies only for idempotent operations |
| Spring Batch | `spring-boot-starter-batch` | **Defer** | Batch-oriented ETL is not a remote orchestration engine |
| Spring Cloud | release-train modules | **Defer** | Avoid premature service discovery/gateway complexity |
| Spring Integration | adapters/gateway | **Defer** | Add only for a demonstrated messaging integration gap |
| Spring Session | persistence support | **Later** | When operator sessions/OIDC require it |
| Spring Authorization Server | dedicated identity server | **Defer** | Prefer external established OIDC identity provider |

### Why Spring Modulith?

It enforces a coherent, explicit domain boundary for *pods*, *secrets*, *inventory*, *plans*, *policy*, *jobs*, *integrations* and *audit*, without the operational complexity of dozens of services. Each package becomes a module, public APIs are restricted, and circular dependencies are detected.

### Why Spring AI?

Spring AI supplies a common Java programming model for `ChatModel`, `ChatClient`, structured outputs, tool definitions and observability. It **does not** implement approvals or a trusted infrastructure execution engine. The proprietary **Codex agent SDK/runtime** is not equivalent to choosing an OpenAI model inside Spring AI: implement an isolated Codex bridge if real coding-agent behavior is needed, without direct server credentials.

### Why not n8n as the backend?

n8n is useful for notifications and recurring workflows, but is not a suitable source of truth for authorization, secrets isolation, infrastructure safety, job leases and module-based domain logic. Optional integrations can emit events to n8n; they never bypass Monique policy.

## BOM example (planned upgrade, pin exact versions)

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.modulith</groupId>
      <artifactId>spring-modulith-bom</artifactId>
      <version>2.1.1</version>
      <type>pom</type><scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>2.0.2</version>
      <type>pom</type><scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Do not instantiate AI provider beans at startup without handling missing API keys gracefully. First refactor the existing hand-written provider adapter to Spring AI and introduce provider-specific configuration only with tests and a `planning-only` mode.

## Official references

- [Spring Boot 4.1.2 announcement](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/)
- [Spring Modulith latest stable and BOM](https://docs.spring.io/spring-modulith/reference/)
- [Spring Modulith structure/verification](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- [Spring AI 2.0.x Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html)
- [Anthropic starter](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html)
- [OpenAI starter](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)

## Observability and threat-detection extensions

**Build target, not yet added to the current `backend/pom.xml`.** These are integration dependencies for the **Spring control plane**, not components to package into the Java application itself.

| Dependency / system | Maven coordinates or integration contract | Priority | Purpose |
|---|---|---|---|
| Spring Boot Actuator | `org.springframework.boot:spring-boot-starter-actuator` | First | `health`, `info`, `prometheus`, readiness/liveness, internal operational diagnostics |
| Micrometer Prometheus | `io.micrometer:micrometer-registry-prometheus` | First | Publish metrics at `/actuator/prometheus`; private Prometheus scraping |
| Micrometer tracing bridge | `io.micrometer:micrometer-tracing-bridge-otel` | Next | Associate web requests, model calls and durable job traces |
| OpenTelemetry exporter | `io.opentelemetry:opentelemetry-exporter-otlp` | Next | Send sampled traces to a chosen collector/backend |
| Prometheus HTTP API | Bounded read-only `/api/v1/query`, `/api/v1/query_range` connector | Next | Query metrics without granting arbitrary HTTP/SSRF capabilities to the model |
| Grafana HTTP API | Least-privilege API service account / dashboard links | Optional | Provision/query dashboard metadata or link to a secure viewer |
| Wazuh REST API | Version-aware read-only adapter | Optional | Manager/agent status, rules, FIM findings and security context |
| Wazuh indexer search | Separate indexer credentials / approved queries only | Optional | Historical security alert retrieval beyond manager API |
| Spring Modulith | `observability` and `securitymonitoring` logical modules | Next | Encapsulated adapters and normalized evidence APIs |

**No Spring WebFlux requirement:** the existing MVC backend can perform bounded outbound REST calls and stream job progress via SSE. Introduce WebFlux only if a demonstrated concurrency/streaming need justifies it.

**Minimal Maven additions (after testing the current backend build):**

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**Endpoint principle:** Prometheus scrape exposure requires explicitly enabling that endpoint in configuration **and** firewall/authenticated network access. Expose `health` only as appropriate for uptime checks, and never publish `heapdump`, `env`, `logfile`, `configprops` or other sensitive Actuator endpoints publicly. Do not expose all actuator endpoints indiscriminately.

Prometheus, Grafana and Wazuh are **separate services**, not Spring starters. Monique integrates with them via their supported interfaces; Grafana is not required to run PromQL queries.

See [Observability](OBSERVABILITY.md), [Security Monitoring](SECURITY-MONITORING.md) and [ADR](adr/0001-observability-security-stack.md).


## External database management versus Spring application dependencies

MONIQUE managing RabbitMQ, Redis or MongoDB **does not** mean MONIQUE itself must use those technologies. Avoid unnecessary Spring starters and infrastructure dependencies:

| Technology | Role in MONIQUE backend |
|---|---|
| Spring JDBC + PostgreSQL | **Existing** metadata database for Monique itself |
| Spring Data JDBC / JPA | Optional aggregate persistence refactor |
| Spring Data Redis | **Not needed** unless Monique itself uses Redis for caching/locks |
| Spring Data MongoDB | **Not needed** unless Monique itself stores application data in MongoDB |
| Spring AMQP / RabbitMQ | **Not needed** to manage a remote RabbitMQ broker via its HTTP API; only add if Monique itself uses AMQP |
| Spring for Apache Kafka | **Not needed** to administer a remote Kafka cluster; only add for Monique's own event pipeline |
| Spring Actuator + Micrometer | Proposed health metrics for the control plane and normalized monitoring |

Prefer typed REST/CLI adapters in isolated workers for lifecycle operations, with compatibility-tested vendor SDKs only where appropriate. Database data, backup objects, RabbitMQ secrets and message contents never pass through model context or ordinary application logs.

See [Data Services](DATA-SERVICES.md).


## Integration delivery technology choices

The 92-entry roadmap is intentionally **not 92 Spring starters**. Keep provider SDKs inside isolated adapter modules/workers and add dependencies only when a provider implementation is actually tested:

- **Spring Web MVC + RestClient** for bounded JSON REST APIs (Coolify, Scalingo, Hetzner, DigitalOcean, Cloudflare, Portainer, Render and others); authentication per provider, never a global admin token.
- **GraphQL client where justified** for Railway; parse GraphQL `errors` even on HTTP 200 and enforce schema/version compatibility.
- **Official SDKs** only when mature and available for the target Java/worker runtime; prefer generated typed clients for vendor OpenAPI specs with contract tests.
- **SSH/SFTP + restricted CLI worker** for approved host-local operations such as restic, Docker Compose, OS services and panel bootstrap when no official application API exists. No direct Docker daemon socket to the web process.
- **AWS/S3-compatible client** for object storage, checking compatibility per provider (S3, R2, MinIO, Wasabi); encryption and key scope per bucket.
- **Kubernetes Java client** only in the advanced cluster module; prohibit cluster-admin default roles and unspecified namespaces.
- **Spring Modulith contracts** for `integrationcatalog`, not one Spring module per external vendor by default.

**Status:** these are target architecture decisions, not newly added Maven dependencies. See [INTEGRATIONS.md](INTEGRATIONS.md).
