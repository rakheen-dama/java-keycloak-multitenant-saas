# java-keycloak-multitenant-saas

[![Java 25](https://img.shields.io/badge/Java-25-blue)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0.2-green)](https://spring.io/projects/spring-boot)
[![Keycloak 26](https://img.shields.io/badge/Keycloak-26.5-orange)](https://www.keycloak.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Multi-tenant SaaS starter template with schema-per-tenant isolation, Keycloak organizations, and a Spring Cloud Gateway BFF.

## Architecture

```
Browser → Gateway (8443) → Backend (8080) → PostgreSQL (5432)
                ↓
          Keycloak (8180) — OIDC, organizations, invitations
```

Each Keycloak organization maps to an isolated PostgreSQL schema (`tenant_<hash>`).
The Gateway handles OAuth2 login and relays tokens to the backend via `TokenRelay`.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Spring Boot 4.0.2, Java 25, Hibernate 7, Flyway |
| Gateway | Spring Cloud Gateway (BFF pattern) |
| Auth | Keycloak 26.5 (organizations, RBAC) |
| Database | PostgreSQL 16, schema-per-tenant multitenancy |
| Email | Mailpit (local dev) |

## Quick Start

### Prerequisites

- Java 25 (GraalVM or OpenJDK)
- Maven 3.9+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
bash compose/scripts/dev-up.sh
```

This starts PostgreSQL, Keycloak, and Mailpit. Wait for all health checks to pass.

### 2. Start Backend

```bash
cd backend
./mvnw spring-boot:run
```

### 3. Start Gateway

```bash
cd gateway
./mvnw spring-boot:run
```

### 4. Access Services

| Service | URL |
|---------|-----|
| Gateway | http://localhost:8443 |
| Keycloak Admin | http://localhost:8180 (admin/admin) |
| Mailpit UI | http://localhost:8025 |

### Stop Infrastructure

```bash
bash compose/scripts/dev-down.sh          # Preserve data
bash compose/scripts/dev-down.sh --clean  # Wipe volumes
```

## Project Structure

```
.
├── backend/          # Spring Boot 4 REST API (port 8080)
├── gateway/          # Spring Cloud Gateway BFF (port 8443)
├── compose/          # Docker Compose, Keycloak realm, dev scripts
│   ├── docker-compose.yml
│   ├── keycloak/     # Realm export
│   ├── data/postgres # Init SQL
│   └── scripts/      # dev-up.sh, dev-down.sh
├── pom.xml           # Maven parent POM (aggregator)
└── docs/             # Architecture documentation
```

## License

[MIT](LICENSE) - Rakheen Dama
