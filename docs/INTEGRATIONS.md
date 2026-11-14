# Integration catalog — MONIQUE

**ID:** MON-INT-001 · **Updated:** 2026-11-14 · **Scope:** roadmap candidate assessment, **not implemented**.

## Product decision

MONIQUE supports **several different resource types**: a `Pod` is an SSH-managed host; a `ConnectedAccount` belongs to a provider or platform; a `RemoteResource` is an app, service, instance, deployment, game server, VM, DNS zone, domain or database discovered through an adapter. An external platform **does not become a Pod** merely because Monique can manage it.

Use APIs and SDKs before shell automation where appropriate. `official_api` = official vendor documentation was located; `standard_api` = a recognized ecosystem API/protocol can be used subject to provider compatibility; `cli_protocol` = CLI-based tooling requiring a trusted worker; `ssh_cli` = discovery and host-local commands, **not evidence of an official REST API**; `evaluate` = design candidate pending compatibility/security review.

No connector is declared **supported** until tests for authorization, version negotiation, read-only inventory, errors, rate limits, and negative cases pass.

## Priorities

- **P0 — Foundation:** integrations needed for Monique's initial use cases and trusted operational primitives.
- **P1 — Common:** highly requested hosting, deployment, cloud, DNS and diagnostic providers.
- **P2 — Expansion:** other widely used tools and managed platforms after baseline reliability.
- **P3 — Specialized:** advanced cloud/orchestration or niche panels, delivered based on demand.

**All 92 entries are planned candidates, not shipped integrations.** Priority indicates proposed implementation order, **not measured market share**. An `official_api` or `standard_api` tag means a documented integration surface was identified—not that Monique supports it today; `evaluate` means version-specific documentation and feasibility still need review.

## Catalog (129 planned integrations)

### Game & hosting panels

| Integration | Priority | Interface assessment |
|---|---|---|
| [Pterodactyl 1.x / 2.x](https://docs.pterodactyl.io/) | P0 | `official_api` |
| [Pelican Panel](https://pelican.dev/docs/panel/advanced/api-docs/) | P1 | `official_api` |
| CubeCoders AMP | P2 | `evaluate` |
| PufferPanel | P2 | `evaluate` |
| Crafty Controller | P2 | `evaluate` |
| Multicraft | P3 | `evaluate` |
| TCAdmin | P3 | `evaluate` |
| [cPanel / WHM](https://api.docs.cpanel.net/) | P1 | `official_api` |
| Plesk | P1 | `evaluate` |
| DirectAdmin | P2 | `evaluate` |
| HestiaCP | P2 | `evaluate` |
| aaPanel | P2 | `evaluate` |
| CyberPanel | P3 | `evaluate` |
| CloudPanel | P1 | `ssh_cli` |
| Webmin / Virtualmin | P2 | `evaluate` |

### Self-hosted PaaS & containers

| Integration | Priority | Interface assessment |
|---|---|---|
| [Coolify](https://coolify.io/docs/api/overview) | P0 | `official_api` |
| [Dokploy](https://docs.dokploy.com/docs/api) | P1 | `official_api` |
| [Portainer](https://docs.portainer.io/api/docs) | P1 | `official_api` |
| [CapRover](https://caprover.com/docs/reference/api) | P1 | `official_api` |
| Easypanel | P2 | `evaluate` |
| [Docker Engine](https://docs.docker.com/reference/api/engine/) | P0 | `official_api` |
| Docker Compose | P0 | `cli_protocol` |
| Podman | P2 | `evaluate` |
| [Kubernetes](https://kubernetes.io/docs/reference/using-api/) | P2 | `standard_api` |
| [K3s](https://docs.k3s.io/) | P2 | `standard_api` |
| Rancher | P3 | `evaluate` |
| HashiCorp Nomad | P3 | `evaluate` |
| Proxmox VE | P1 | `evaluate` |

### Managed platforms / PaaS

| Integration | Priority | Interface assessment |
|---|---|---|
| [Scalingo](https://developers.scalingo.com/) | P1 | `official_api` |
| [Railway](https://docs.railway.com/integrations/api) | P1 | `official_api` |
| [Render](https://render.com/docs/api) | P1 | `official_api` |
| [Fly.io](https://machines-api-spec.fly.dev/) | P1 | `official_api` |
| [Heroku](https://devcenter.heroku.com/articles/platform-api-reference) | P2 | `official_api` |
| [Vercel](https://vercel.com/docs/rest-api) | P2 | `official_api` |
| Netlify | P2 | `evaluate` |
| DigitalOcean App Platform | P2 | `evaluate` |
| Google Cloud Run | P3 | `evaluate` |
| Azure App Service | P3 | `evaluate` |
| AWS ECS / Fargate | P3 | `evaluate` |

### Cloud / VPS / dedicated

| Integration | Priority | Interface assessment |
|---|---|---|
| [Hetzner Cloud](https://docs.hetzner.cloud/) | P0 | `official_api` |
| [Hetzner Robot dedicated](https://docs.hetzner.cloud/) | P1 | `official_api` |
| OVHcloud VPS / bare metal | P1 | `evaluate` |
| Scaleway Instances | P1 | `evaluate` |
| [DigitalOcean Droplets](https://docs.digitalocean.com/products/droplets/reference/api/) | P1 | `official_api` |
| [Amazon EC2](https://docs.aws.amazon.com/AWSEC2/latest/APIReference/) | P2 | `official_api` |
| Akamai / Linode | P2 | `evaluate` |
| Vultr | P2 | `evaluate` |
| Contabo | P2 | `evaluate` |
| Oracle Cloud Infrastructure | P3 | `evaluate` |
| Google Compute Engine | P3 | `evaluate` |
| Azure Virtual Machines | P3 | `evaluate` |

### DNS / network / ingress

| Integration | Priority | Interface assessment |
|---|---|---|
| [Cloudflare DNS](https://developers.cloudflare.com/api/resources/dns/subresources/records/methods/list/) | P0 | `official_api` |
| [Hetzner DNS](https://docs.hetzner.cloud/) | P1 | `official_api` |
| AWS Route 53 | P2 | `evaluate` |
| OVHcloud DNS | P2 | `evaluate` |
| NGINX | P0 | `ssh_cli` |
| Caddy | P1 | `ssh_cli` |
| Traefik | P1 | `ssh_cli` |
| Nginx Proxy Manager | P1 | `evaluate` |
| HAProxy | P2 | `ssh_cli` |
| Tailscale | P1 | `evaluate` |
| WireGuard | P1 | `ssh_cli` |
| [Let's Encrypt / ACME](https://letsencrypt.org/docs/) | P0 | `standard_api` |

### Databases / storage / backup

| Integration | Priority | Interface assessment |
|---|---|---|
| [PostgreSQL](https://www.postgresql.org/docs/current/) | P0 | `standard_api` |
| [MySQL](https://dev.mysql.com/doc/) | P0 | `standard_api` |
| [Redis](https://redis.io/docs/latest/) | P1 | `standard_api` |
| [MongoDB](https://www.mongodb.com/docs/manual/) | P2 | `standard_api` |
| [Amazon S3](https://docs.aws.amazon.com/AmazonS3/latest/API/Welcome.html) | P1 | `standard_api` |
| [Cloudflare R2](https://developers.cloudflare.com/r2/api/) | P1 | `official_api` |
| MinIO | P1 | `standard_api` |
| Backblaze B2 | P2 | `evaluate` |
| Wasabi | P2 | `standard_api` |
| restic | P0 | `cli_protocol` |
| BorgBackup | P2 | `cli_protocol` |
| rclone | P1 | `cli_protocol` |
| pgBackRest | P2 | `cli_protocol` |
| [MariaDB](https://mariadb.com/docs/server/) | P0 | `standard_api` |
| [Valkey](https://valkey.io/topics/) | P1 | `standard_api` |
| [SQLite (embedded)](https://www.sqlite.org/docs.html) | P1 | `cli_protocol` |
| [ClickHouse](https://clickhouse.com/docs) | P2 | `official_api` |
| [TimescaleDB (PostgreSQL extension)](https://docs.timescale.com/) | P2 | `standard_api` |
| [InfluxDB](https://docs.influxdata.com/) | P2 | `official_api` |
| [OpenSearch](https://docs.opensearch.org/) | P2 | `official_api` |
| [Elasticsearch](https://www.elastic.co/docs) | P2 | `official_api` |
| [Meilisearch](https://www.meilisearch.com/docs) | P2 | `official_api` |
| [Qdrant](https://qdrant.tech/documentation/) | P2 | `official_api` |
| [Dragonfly](https://www.dragonflydb.io/docs) | P2 | `evaluate` |
| [Memcached](https://docs.memcached.org/) | P2 | `standard_api` |
| [Typesense](https://typesense.org/docs/) | P3 | `official_api` |
| [Weaviate](https://docs.weaviate.io/) | P3 | `official_api` |
| [Milvus](https://milvus.io/docs) | P3 | `official_api` |
| [Neo4j](https://neo4j.com/docs/) | P3 | `standard_api` |
| [Apache Cassandra](https://cassandra.apache.org/doc/latest/) | P3 | `standard_api` |
| [ScyllaDB](https://docs.scylladb.com/) | P3 | `standard_api` |
| [CockroachDB](https://www.cockroachlabs.com/docs/) | P3 | `standard_api` |
| [Apache CouchDB](https://docs.couchdb.org/) | P3 | `official_api` |
| [KeyDB](https://docs.keydb.dev/) | P3 | `evaluate` |

### Messaging / event streaming

| Integration | Priority | Interface assessment |
|---|---|---|
| [RabbitMQ (AMQP)](https://www.rabbitmq.com/docs/management) | P0 | `official_api` |
| [Apache Kafka](https://kafka.apache.org/documentation/) | P2 | `standard_api` |
| [NATS / JetStream](https://docs.nats.io/) | P2 | `standard_api` |
| [Redpanda](https://docs.redpanda.com/) | P2 | `evaluate` |
| [Apache ActiveMQ Artemis](https://artemis.apache.org/documentation/) | P3 | `evaluate` |
| [Apache Pulsar](https://pulsar.apache.org/docs/) | P3 | `evaluate` |
| [EMQX (MQTT broker)](https://docs.emqx.com/) | P2 | `evaluate` |
| [Eclipse Mosquitto](https://mosquitto.org/documentation/) | P2 | `cli_protocol` |

### Managed database services

| Integration | Priority | Interface assessment |
|---|---|---|
| [Supabase](https://supabase.com/docs) | P1 | `evaluate` |
| [Neon](https://neon.tech/docs) | P2 | `evaluate` |
| [MongoDB Atlas](https://www.mongodb.com/docs/atlas/) | P2 | `evaluate` |
| [PlanetScale](https://planetscale.com/docs) | P2 | `evaluate` |
| [Upstash](https://upstash.com/docs) | P2 | `evaluate` |
| [Aiven managed databases/brokers](https://aiven.io/docs) | P2 | `evaluate` |
| [Redis Cloud](https://redis.io/docs/latest/operate/rc/) | P2 | `evaluate` |
| [CloudAMQP (managed RabbitMQ)](https://www.cloudamqp.com/docs/) | P2 | `evaluate` |

### DevOps / monitoring / security

| Integration | Priority | Interface assessment |
|---|---|---|
| [GitHub Actions](https://docs.github.com/en/rest/actions) | P1 | `evaluate` |
| [GitLab CI/CD](https://docs.gitlab.com/api/pipelines/) | P1 | `official_api` |
| Forgejo / Gitea | P2 | `evaluate` |
| Jenkins | P3 | `evaluate` |
| Argo CD | P3 | `evaluate` |
| [Prometheus](https://prometheus.io/docs/prometheus/latest/querying/api/) | P0 | `standard_api` |
| [Grafana](https://grafana.com/docs/grafana/latest/) | P1 | `standard_api` |
| [Wazuh](https://documentation.wazuh.com/current/user-manual/api/index.html) | P2 | `standard_api` |
| Uptime Kuma | P1 | `evaluate` |
| Grafana Loki | P2 | `evaluate` |
| Prometheus Alertmanager | P2 | `standard_api` |
| Sentry | P2 | `evaluate` |
| Zabbix | P2 | `evaluate` |
| CrowdSec | P2 | `evaluate` |
| Fail2ban | P1 | `ssh_cli` |
| n8n | P2 | `evaluate` |

## Top workflows and boundaries

| User request | Appropriate connector | Capability boundaries |
|---|---|---|
| “Create a Paper Minecraft server and import an egg.” | Pterodactyl/Pelican + verified Pod allocation | Panel version and API privilege matter; verify node/Wings compatibility and memory/port capacity |
| “Move Coolify from Pod 2 to Pod 1.” | Coolify + Docker/DB/backup + DNS | Migrate actual application volumes and DBs separately, protect existing workloads, no guaranteed zero downtime |
| “Deploy my Spring Boot API on Scalingo and scale it.” | **Scalingo regional HTTPS API** | **Managed PaaS:** app, builds, deployment, variables/add-ons/scale as documented; **no root SSH, no Pterodactyl install** |
| “Rollback my Railway service.” | Railway GraphQL | Handle GraphQL `errors` even on HTTP 200; honor token scopes and rate limits |
| “Provision a new VPS with two IPs.” | Hetzner Cloud / DigitalOcean / other provider API | Verify assigned and routable IPs, region-specific limits, credentials, billing and destructive-action approvals |
| “Create a new DNS A record during cutover.” | Cloudflare DNS / provider DNS | Preview diff/TTL/proxy mode, verify target health, approval required for DNS changes |
| “Create a Pterodactyl backup in S3.” | Pterodactyl + S3-compatible storage | Test actual restore, object permissions and retention; S3-compatible does not mean every API feature works identically |
| “Diagnose my server's crashes.” | Prometheus/Grafana, optional Wazuh | Correlation yields evidence; never grants autonomous write permission |
| “Move my site from cPanel to Plesk.” | cPanel/WHM + Plesk + backup/DNS adapters | Verify version, website/database/mail/data migration independently; no one-click migration promise |
| “Deploy the code from GitHub Actions.” | GitHub/GitLab API + selected deployment platform | CI/CD jobs and deploy permissions remain isolated from root administration |

### Scalingo (specific engineering contract)

Scalingo is a PaaS, **not a dedicated server/VPS provider**. Its API is region-specific; the documented version is **v1 over HTTPS**, and token acquisition/rotation must follow Scalingo authentication requirements. A future `ScalingoAdapter` should support discovery of region/apps, environment/deploy status, add-ons, scaling and logs **only where the verified API and permission scopes allow**. No access to its underlying OS/root filesystem is assumed. Migrations between Scalingo and self-hosted Coolify are **application-level migrations** requiring source/runtime compatibility, database dump/restore, add-on equivalence, config/secrets mapping and DNS cutover—not copying an entire VPS image.

Official API: https://developers.scalingo.com/ .

### Pterodactyl compatibility

The **1.x Client and Application APIs** and **2.0 Client, Application and Admin APIs** differ. Do not assume eggs, nests, allocations, admin privileges or extensions are managed identically. Explicitly negotiate major version; reject unsupported operations rather than guessing.

Official references: https://docs.pterodactyl.io/v1/api and https://docs.pterodactyl.io/v2/api .

### Hetzner compatibility

Use **Hetzner Cloud API** for Cloud VPS, networks, primary/floating IPs, firewalls and volumes. **Robot dedicated servers** have a separate API/service surface. Do not treat a Hetzner Dedicated host as a Cloud server resource; permissions and operations differ.

Official reference: https://docs.hetzner.cloud/ .

## Stateful data service capability semantics

Every SQL, NoSQL, cache, broker, search/vector or managed database integration must independently advertise and test `inspect`, `install`, `configure`, `backup`, `restore`, `migrate`, and `monitor` as relevant. **Installation does not imply migration, backup or high-availability support.** RabbitMQ topology/definitions and queued messages require separate migration strategies; Kafka and NATS do not use RabbitMQ AMQP operations.

See [DATA-SERVICES.md](DATA-SERVICES.md) and [ADR 0003](adr/0003-data-services-and-brokers.md).

## Kubernetes and K3s traffic routing (target capabilities)

Kubernetes/K3s already appear in this catalog. The following features are **resource capabilities within those integrations**, rather than additional external providers: `services.clusterip.read/create/update`, `services.nodeport.read/create/update`, `services.loadbalancer.read/create/update`, `services.externalname.read/create/update`, `ingress.inspect/plan`, `gateway.inspect/plan`, `httproute.inspect/plan`, and controller/EndpointSlice health checks.

Write operations remain **not implemented**. Scope credentials to namespaces, check controller/CRD support, external IP/port/firewall/TLS status, and block unsupported or unsafe actions. `Ingress` requires a controller; the community **ingress-nginx project is retired** and should not be the default new deployment. K3s Traefik/ServiceLB may consume node ports 80/443.

See [Kubernetes Networking](KUBERNETES-NETWORKING.md).


## Integration delivery gates

1. Confirm official documentation and version(s), license/commercial terms, and maintenance.
2. Implement read-only discovery first with a least-privilege token, OAuth flow or scoped credential.
3. Register resource model, provider account, endpoint allowlist, region, version, scopes, rate limits and timeout policy.
4. Handle pagination, retries with backoff, async operation IDs, quota/billing constraints, and idempotency per operation.
5. Include a normalized capability map; unsupported operations must return **UNSUPPORTED**, never simulate success.
6. Require preflight, protected-resource checks, a stored plan hash and explicit approval for writes.
7. Provide test fixtures, sandbox integration tests and failure-injection tests; verify restore before destructive migration.
8. Create/update a provider-specific runbook linked from the adapter before marking it **SUPPORTED** in the registry.

Machine-readable source: [catalog.json](integrations/catalog.json). Detailed interface design: [Integration Architecture](INTEGRATION-ARCHITECTURE.md). Decision: [ADR 0002](adr/0002-provider-integration-model.md).
