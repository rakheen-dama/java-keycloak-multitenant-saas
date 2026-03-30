# CLAUDE.md

Multi-tenant SaaS starter template with Keycloak organizations and schema-per-tenant isolation.
Monorepo: `backend/`, `gateway/`, `compose/`.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.2, Java 25, Hibernate 7, Flyway |
| Gateway | Spring Cloud Gateway (BFF, TokenRelay) |
| Auth | Keycloak 26.5 (organizations, RBAC) |
| Database | PostgreSQL 16, schema-per-tenant multitenancy |
| Email | Mailpit (local dev capture) |

## Base Package

`io.github.rakheendama.starter`

## Local Dev Quick Start

```bash
# Start infrastructure (Postgres, Keycloak, Mailpit)
bash compose/scripts/dev-up.sh

# Backend (from backend/)
./mvnw spring-boot:run        # Port 8080

# Gateway (from gateway/)
./mvnw spring-boot:run        # Port 8443

# Stop infrastructure
bash compose/scripts/dev-down.sh          # Preserve data
bash compose/scripts/dev-down.sh --clean  # Wipe volumes
```

## Service Ports

| Service | Port | Notes |
|---------|------|-------|
| Backend | 8080 | Spring Boot 4 REST API |
| Gateway | 8443 | Spring Cloud Gateway BFF |
| Keycloak | 8180 | OIDC, organizations, invitations |
| PostgreSQL | 5432 | App + Keycloak data |
| Mailpit SMTP | 1025 | Dev email capture |
| Mailpit UI | 8025 | Email inspector |

## Spring Boot 4 Gotchas (Breaking Changes from Boot 3)

| Class | Old Package (Boot 3) | New Package (Boot 4) |
|-------|---------------------|---------------------|
| `HibernatePropertiesCustomizer` | `boot.orm.jpa` | `boot.hibernate.autoconfigure` |
| `AutoConfigureMockMvc` | `boot.test.autoconfigure.web.servlet` | `boot.webmvc.test.autoconfigure` |

## Hibernate 7 Multitenancy Rules

- **No `hibernate.multiTenancy` property** — Hibernate 7 auto-detects multitenancy from the registered `MultiTenantConnectionProvider`. Setting this property is an error.
- `MultiTenantConnectionProvider<String>` in Hibernate 7 requires implementing `getReadOnlyConnection()` and `releaseReadOnlyConnection()` in addition to the standard `getConnection()`/`releaseConnection()`.
- Use `MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER` and `MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER` constants (not string keys) when registering via `HibernatePropertiesCustomizer`.

## Java 25 ScopedValues

- Use `ScopedValue.where(key, val).run(() -> ...)` to bind — values are automatically unbound when the lambda exits.
- Never call `.get()` without first checking `.isBound()` — throws `NoSuchElementException` when unbound.
- Prefer `requireXxx()` convenience methods that throw meaningful exceptions.
- ScopedValues are virtual-thread-safe: zero per-thread overhead, unlike ThreadLocal.

## Code Style

- No Lombok — use Java 25 records for DTOs, request/response objects, value objects.
- Constructor injection only — no `@Autowired` on fields.
- `ResponseEntity` for all controller return types.
- `ProblemDetail` (RFC 9457) for error responses via `@RestControllerAdvice`.
- Organize by feature package: each domain package contains entity + repository + service + controller.

## Plugin Versions (Java 25 Compatibility)

- `spotless-maven-plugin`: **3.2.1+** (2.x crashes with `NoSuchMethodError` on Java 25)
- `google-java-format`: **1.28.0+**

## Test Conventions

- Never `@ActiveProfiles("local")` in tests — use `@ActiveProfiles("test")`. The `local` profile connects to Docker Compose Postgres.
- `TestcontainersConfiguration` class must be `public` — package-private breaks `@Import` from subpackages.
- `@AutoConfigureMockMvc` comes from `org.springframework.boot.webmvc.test.autoconfigure` in Boot 4.

## Docker Entry Point

Use `org.springframework.boot.loader.launch.JarLauncher` — never `java -jar`.

## Schema Naming Convention

`tenant_` + first 12 hex chars of `SHA-256(orgSlug)`

Example: `orgSlug = "acme-corp"` → `tenant_a3f2b1c4d5e6`

## Keycloak Realm

- Realm: `starter`
- Client `starter-gateway`: confidential, authorization code flow
- Client `starter-admin-cli`: service account for backend Admin API calls
- Groups claim mapper: `groups` (platform-admin detection)
- Organization claim mapper: `organization` (tenant resolution from JWT)

## Current Progress

| Slice | Name | PR | Status |
|-------|------|----|--------|
| T1A | Compose + Repo scaffold | #2 | Done |
| T1B | Backend skeleton + multitenancy core | #3 | Done |
| T1C | Migrations + integration tests | #4 | Done |
| T2A | Gateway Maven project + security config | #5 | Done |
| T2B | Backend security + filters | #6 | Done |
| T2C | Keycloak bootstrap + integration tests | #7 | Done |
| T3A | AccessRequest entity + OTP + public endpoints | #8 | Done |
| T3B | Platform admin + provisioning pipeline | #9 | Done |
| T3C | Frontend: Request Access wizard | #10 | Done |
| T3D | Frontend: Platform Admin queue | #11 | Done |
| T4A | Backend: Member sync + CRUD + tests | #12 | Done |
| T4B | Frontend: Members page + invite dialog | #13 | Done |
| T5A | Backend: Customer + Project entities + CRUD | #14 | Done |
| T5B | Backend: Comment entity + member-authored CRUD | #15 | Done |
| T5C | Frontend: Dashboard + Projects pages | #16 | Done |
| T5D | Frontend: Customers pages | #17 | Done |
| T6A | Backend: Magic link + portal JWT + auth filter | #18 | Done |
| T6B | Backend: Portal endpoints + customer comments | #19 | Done |
| T6C | Frontend: Portal pages + Share Portal Link | #20 | Done |
| T7A | Blog posts 01-05 (Foundation through Registration) | #21 | Done |
| T7B | Blog posts 06-10 (Members through Portal Comments) | #22 | Done |

## Anti-Patterns (Do NOT)

- Do not use `ThreadLocal` — use `ScopedValue` (Java 25, virtual-thread-safe)
- Do not set `hibernate.multiTenancy` property — Hibernate 7 auto-detects
- Do not use `@Autowired` on fields — constructor injection only
- Do not use Lombok — use records and explicit constructors
- Do not use `java -jar` in Dockerfiles — use `JarLauncher`
- Do not use spotless-maven-plugin < 3.2.1 — crashes on Java 25
