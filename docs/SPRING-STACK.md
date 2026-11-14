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
| Spring Boot Actuator | `spring-boot-starter-actuator` | **Next** | Health, metrics, readiness; restricted exposure |
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
