---
title: "One-Command Dev Environment"
description: "How bash compose/scripts/dev-up.sh starts PostgreSQL, Keycloak, and Mailpit with health checks, realm import, and platform admin bootstrap — from zero to running in under two minutes."
series: "Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4"
post: 2
---

# One-Command Dev Environment

Nothing kills adoption of a starter template faster than a 45-minute setup ritual. This template
ships with a single script that brings up every infrastructure dependency, configures Keycloak,
and creates a platform admin — all with health-check gates so nothing starts before its
dependencies are ready.

```bash
bash compose/scripts/dev-up.sh
```

That's it. Let's look at what it does.

---

## The Docker Compose Stack

The infrastructure lives in `compose/docker-compose.yml` — three services, no orchestration
frameworks, no Kubernetes YAML:

```yaml
name: starter-dev

services:
  postgres:
    image: postgres:16-alpine
    container_name: starter-postgres
    restart: unless-stopped
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}
      POSTGRES_DB: ${POSTGRES_DB:-app}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./data/postgres:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-postgres} -d ${POSTGRES_DB:-app}"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - dev-network

  keycloak:
    image: quay.io/keycloak/keycloak:26.5.0
    container_name: starter-keycloak
    restart: unless-stopped
    command: start-dev --import-realm
    environment:
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
      KC_DB_USERNAME: ${POSTGRES_USER:-postgres}
      KC_DB_PASSWORD: ${POSTGRES_PASSWORD:-changeme}
      KC_HOSTNAME_STRICT: "false"
      KC_HTTP_ENABLED: "true"
      KC_HTTP_PORT: 8180
      KC_FEATURES: organizations
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-admin}
      KEYCLOAK_CLIENT_SECRET: ${KEYCLOAK_CLIENT_SECRET:-starter-gateway-secret}
    volumes:
      - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json:ro
    ports:
      - "8180:8180"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8180/realms/starter || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 30s
    networks:
      - dev-network

  mailpit:
    image: axllent/mailpit:latest
    container_name: starter-mailpit
    restart: unless-stopped
    ports:
      - "1025:1025"    # SMTP
      - "8025:8025"    # Web UI
    networks:
      - dev-network
```

Three things to notice:

1. **Health-check gates everywhere.** Keycloak won't start until PostgreSQL reports healthy
   (`service_healthy` condition). The dev-up script won't proceed to bootstrap until Keycloak's
   realm endpoint responds.

2. **Keycloak shares the PostgreSQL instance** but uses a separate `keycloak` database (created by
   `init.sql`). One fewer container to manage; full isolation between app data and Keycloak data.

3. **All ports are deterministic** — no random port assignment. You always know where things are.

---

## init.sql — Why Two Databases

The file at `compose/data/postgres/init.sql` is exactly one line of consequence:

```sql
CREATE DATABASE keycloak;
```

PostgreSQL's `docker-entrypoint-initdb.d` scripts run **once** on first container initialization.
The `POSTGRES_DB` environment variable creates the `app` database automatically; this script adds
the `keycloak` database. Both share one PostgreSQL instance but are isolated databases.

> **Why not separate containers?** Keycloak's database is lightweight (metadata, not
> tenant data). A second PostgreSQL container adds memory overhead and Docker Compose complexity
> for no meaningful isolation benefit in dev.

---

## Keycloak Realm Import

Keycloak starts with `--import-realm`, which reads `compose/keycloak/realm-export.json` and
creates the `starter` realm on first boot. Here's what the realm configures:

- **Realm name:** `starter`
- **`organizationsEnabled: true`** — enables the Keycloak organizations feature
- **Client `starter-gateway`:** confidential, authorization code flow, redirect URI to
  `localhost:8443`, `organization` scope requested
- **Client `starter-admin-cli`:** service account with `manage-users`, `manage-clients`,
  and `manage-realm` roles — used by the backend for provisioning
- **SMTP:** points to `mailpit:1025` for dev email capture
- **Groups claim mapper:** includes `groups` claim in access tokens (for platform-admin detection)
- **Organization claim mapper:** includes `organization` claim (for tenant resolution)

You don't need to touch the Keycloak admin UI during normal development. The realm export handles
everything.

---

## keycloak-bootstrap.sh — Platform Admin Setup

The realm import creates clients and mappers, but it can't create users or groups (Keycloak
doesn't export those in realm files). That's what `compose/scripts/keycloak-bootstrap.sh` handles:

1. **Authenticates** `kcadm.sh` using the master realm bootstrap admin credentials
2. **Creates the `platform-admins` group** in the `starter` realm (idempotent — checks before creating)
3. **Creates an initial platform admin user** (`padmin@starter.local` / `password`)
4. **Adds the user to `platform-admins`**

All steps are idempotent — safe to re-run after every restart. The script uses
`docker exec starter-keycloak /opt/keycloak/bin/kcadm.sh`, so you don't need `kcadm` installed
locally.

> **Why a group instead of a realm role?** Keycloak's built-in groups claim mapper makes
> group membership visible in the JWT without custom protocol mappers. The backend checks
> `platform-admins` membership via `RequestScopes.isPlatformAdmin()` — see
> [Post 03](./03-the-multitenancy-core.md) for details.

---

## Mailpit for Email Capture

Mailpit runs on two ports:

- **Port 1025** — SMTP server (used by both Keycloak and the backend)
- **Port 8025** — Web UI for inspecting captured emails

Every email the system sends in dev — OTP codes, Keycloak invitations, magic links — shows up
in the Mailpit UI at `http://localhost:8025`. No real email provider needed.

---

## The dev-up.sh Script

The startup script at `compose/scripts/dev-up.sh` does more than `docker compose up`:

1. Starts the Docker Compose stack (`docker compose up -d`)
2. Waits for each service to report healthy (polling health-check endpoints)
3. Runs `keycloak-bootstrap.sh` to create the platform admin user and group
4. Prints a summary of service URLs

The health-check polling prevents the classic "I started everything but Keycloak isn't ready
yet" race condition. You can trust that when the script finishes, everything is ready.

---

## Tearing Down

Two modes via `compose/scripts/dev-down.sh`:

```bash
bash compose/scripts/dev-down.sh          # Stop containers, preserve data
bash compose/scripts/dev-down.sh --clean  # Stop containers + wipe all volumes
```

The `--clean` flag removes the PostgreSQL data volume, which means the next `dev-up.sh` will
re-run `init.sql` and re-import the Keycloak realm from scratch. Useful when you want a
completely fresh environment.

---

## Starting the Application

With infrastructure running, start the three application processes:

```bash
# Backend (from backend/)
./mvnw spring-boot:run                    # port 8080, uses application-local.yml

# Gateway (from gateway/)
./mvnw spring-boot:run                    # port 8443, TokenRelay to backend

# Frontend (from frontend/)
pnpm dev                                  # port 3000
```

The backend and gateway use `application-local.yml` profiles that point at `localhost` for
PostgreSQL and Keycloak. No environment variables needed for local development.

---

## Service Port Summary

| Service | Port | Notes |
|---------|------|-------|
| Backend | 8080 | Spring Boot 4 REST API |
| Gateway | 8443 | Spring Cloud Gateway BFF |
| Frontend | 3000 | Next.js 16 dev server |
| Keycloak | 8180 | OIDC, organizations, invitations |
| PostgreSQL | 5432 | App + Keycloak data |
| Mailpit SMTP | 1025 | Dev email capture |
| Mailpit UI | 8025 | Email inspector |

---

## What's Next

The dev environment is running. In [Post 03: The Multitenancy Core](./03-the-multitenancy-core.md),
we'll dive into the heart of the template: how a single HTTP request gets routed to the correct
tenant schema using Java 25 ScopedValues, Hibernate's `MultiTenantConnectionProvider`, and a
two-filter chain that binds context without any ThreadLocal.

---

*This is post 2 of 10 in the **Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4** series.*
