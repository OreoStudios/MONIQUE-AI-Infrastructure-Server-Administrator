# ADR 0007 — OreoObfuscator for MONIQUE's Java release hardening

**Date:** 2026-11-14  
**Status:** Accepted as target architecture; implementation and compatibility tests pending

## Context

MONIQUE will be self-hosted and offer monthly paid capabilities. Local Java bytecode can be inspected or patched to bypass product license checks; signed entitlement data cannot make arbitrary client-side code unpatchable. Oreo Studios already develops OreoObfuscator, a Java bytecode transformation tool with CLI, engine and verifier.

## Decision

1. Use **OreoObfuscator** as a production-only, pinned Java obfuscation stage after compilation and before final Spring Boot packaging and artifact signatures.
2. Prefer conservative transformations and keep Spring Boot reflection, autoconfiguration, Spring Modulith module boundaries, serialization DTOs, Security proxy behavior and public SPI stable.
3. Transform only application-owned Java classes until library/JAR structure compatibility is demonstrated; no blind modification of vendor dependencies.
4. Run all meaningful integration, security, license and recovery tests on the **transformed Java 21 production artifact**. OreoObfuscator currently builds under JDK 25, so validate Java 21 independently.
5. Store mapping files privately, keep billing and license-signing keys central and release only signed, verified outputs.
6. Do not assume this protects Tauri Rust, React or the central hosted services; use their native signing/hardening controls.
7. Obfuscation is **deterrence**, never an unbreakable local membership boundary. Centrally hosted premium AI features enforce authorization and usage limits server-side.
8. Keep OreoObfuscator's own tool license distinct from MONIQUE customer memberships.

## Consequences and gate

Adds CI complexity and risk to reflective Spring frameworks; protected release is blocked if verification fails. No actual integration exists until all checklist items in [Release Protection](../RELEASE-PROTECTION.md) are tested.

See [Security](../SECURITY.md) and [Licensing](../LICENSING-AND-BILLING.md).
