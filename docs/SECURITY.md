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
