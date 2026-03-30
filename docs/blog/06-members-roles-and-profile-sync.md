---
title: "Members, Roles & Profile Sync"
description: "How Keycloak identities become tenant members: the MemberFilter first-login flow, profile sync on subsequent requests, invitation via Keycloak API, and why roles live in the product database."
series: "Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4"
post: 6
---

# Members, Roles & Profile Sync

In [Post 05](./05-tenant-registration-pipeline.md) we provisioned a tenant — a Keycloak
organization, a PostgreSQL schema, and a Flyway-migrated set of tables. But the first user
hasn't logged in yet. When they do, we need to answer: **how does a Keycloak identity become
a Member in the tenant schema?**

The answer is a filter, a sync service, and a deliberate decision to keep roles out of
Keycloak entirely.

---

## The Member Entity

`backend/src/main/java/io/github/rakheendama/starter/member/Member.java`:

```java
@Entity
@Table(name = "members")
public class Member {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "keycloak_user_id", nullable = false, unique = true)
  private String keycloakUserId;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "role", nullable = false)
  private String role;          // "owner" or "member"

  @Column(name = "status", nullable = false)
  private String status;        // "ACTIVE" or "SUSPENDED"

  @Column(name = "first_login_at")
  private Instant firstLoginAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  protected Member() {}

  public Member(String keycloakUserId, String email, String displayName, String role) {
    this.keycloakUserId = keycloakUserId;
    this.email = email;
    this.displayName = displayName;
    this.role = role;
    this.status = "ACTIVE";
    this.firstLoginAt = Instant.now();
    this.lastLoginAt = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void syncProfile(String email, String displayName) {
    if (email != null) this.email = email;
    if (displayName != null) this.displayName = displayName;
    this.lastLoginAt = Instant.now();
    this.updatedAt = Instant.now();
  }
}
```

No Lombok. Mutation methods (`syncProfile`, `changeRole`, `suspend`, `activate`) express the
domain operations. The `protected` no-arg constructor satisfies JPA without exposing it to
callers.

---

## MemberFilter — First Login Detection

`MemberFilter` runs after `TenantFilter` in the filter chain. By the time it executes,
`RequestScopes.TENANT_ID` is already bound — Hibernate knows which schema to query.

`backend/src/main/java/io/github/rakheendama/starter/multitenancy/MemberFilter.java`:

```java
@Component
public class MemberFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!RequestScopes.TENANT_ID.isBound()) {
      filterChain.doFilter(request, response);
      return;
    }

    Jwt jwt = extractJwt();
    String keycloakUserId = JwtUtils.extractSub(jwt);

    Member member;
    var memberOpt = memberRepository.findByKeycloakUserId(keycloakUserId);
    if (memberOpt.isEmpty()) {
      // First login — create the Member record
      String keycloakOrgId = JwtUtils.extractOrgId(jwt);
      member = memberSyncService.syncOrCreate(jwt, keycloakOrgId);
    } else {
      // Subsequent login — use existing record, no DB write
      member = memberOpt.get();
    }

    ScopedFilterChain.runScoped(
        ScopedValue.where(RequestScopes.MEMBER_ID, member.getId())
            .where(RequestScopes.ORG_ROLE, member.getRole()),
        filterChain, request, response);
  }
}
```

Two things worth noting:

1. **No DB write on subsequent logins.** The filter reads the existing `Member` and passes it
   through. Profile sync only happens on first login via `syncOrCreate`. This keeps the
   hot path fast — no `UPDATE` on every request.
2. **ScopedValue binding is atomic.** Both `MEMBER_ID` and `ORG_ROLE` are bound in a single
   `ScopedFilterChain.runScoped()` call. When the lambda exits, both are automatically unbound.

---

## MemberSyncService — The First-Login Flow

`backend/src/main/java/io/github/rakheendama/starter/member/MemberSyncService.java`:

```java
@Service
public class MemberSyncService {

  @Transactional
  public Member syncOrCreate(Jwt jwt, String keycloakOrgId) {
    String keycloakUserId = JwtUtils.extractSub(jwt);
    String email = JwtUtils.extractEmail(jwt);
    String displayName = JwtUtils.extractName(jwt);

    return memberRepository
        .findByKeycloakUserId(keycloakUserId)
        .map(existing -> {
          existing.syncProfile(email, displayName);
          return memberRepository.save(existing);
        })
        .orElseGet(() -> {
          String role = determineRole(keycloakOrgId, keycloakUserId);
          var member = new Member(keycloakUserId, email, displayName, role);
          return memberRepository.save(member);
        });
  }

  private String determineRole(String keycloakOrgId, String keycloakUserId) {
    try {
      return keycloakProvisioningClient.isOrgCreator(keycloakOrgId, keycloakUserId)
          ? "owner" : "member";
    } catch (Exception e) {
      return "member";  // safe default
    }
  }
}
```

The `determineRole` method calls `keycloakProvisioningClient.isOrgCreator()` — a Keycloak
Admin API call that checks if this user was the one who triggered the org creation during
provisioning. If yes, they get `"owner"`. Everyone else gets `"member"`. After this initial
assignment, Keycloak is never consulted for roles again.

> **Why catch Exception broadly?** If the Keycloak Admin API is unreachable during a login,
> we'd rather assign `"member"` and let an admin upgrade the role later than block the login
> entirely. A `WARN` log captures the failure for investigation.

---

## Invitation Flow

Owners can invite new members via the Keycloak API. The service delegates to
`KeycloakProvisioningClient.inviteUser()`, which sends a Keycloak organization invitation
email.

`backend/src/main/java/io/github/rakheendama/starter/member/MemberService.java`:

```java
public void inviteMember(String email) {
  RequestScopes.requireOwner();
  var org = organizationRepository.findAll().stream().findFirst()
      .orElseThrow(() -> new InvalidStateException("No organization found", "..."));
  keycloakProvisioningClient.inviteUser(org.getKeycloakOrgId(), email);
}
```

The invitee receives an email, registers with Keycloak, and on their first login,
`MemberFilter` → `MemberSyncService.syncOrCreate()` creates their `Member` record with role
`"member"`.

---

## RBAC: What Each Role Can Do

| Action | Owner | Member |
|--------|-------|--------|
| View projects, customers, comments | Yes | Yes |
| Create/update/archive projects | Yes | No |
| Create/update customers | Yes | No |
| Add comments | Yes | Yes |
| Invite members | Yes | No |
| Remove members | Yes | No |
| Change member roles | Yes | No |

Authorization is enforced by `RequestScopes.requireOwner()` at the service layer:

```java
public static void requireOwner() {
  if (!"owner".equals(ORG_ROLE.get())) {
    throw new ForbiddenException("Owner role required", "This action requires owner privileges");
  }
}
```

Every write operation that should be owner-only calls `requireOwner()` as its first line.
No annotation magic, no aspect — a direct method call that throws if the role doesn't match.

---

## Why Roles Live in the Product Database

This is a deliberate architectural decision documented in
[ADR-T003: Product-Layer Roles Over Keycloak Org Roles](../adr/ADR-T003-product-layer-roles-over-keycloak-org-roles.md).

| Concern | Keycloak Org Roles | Product DB Roles |
|---------|-------------------|------------------|
| Query "all owners" | Admin API call (slow, paginated) | `memberRepository.findByRole("owner")` |
| Role change latency | API round-trip to Keycloak | Single `UPDATE members SET role = ?` |
| Audit trail | Keycloak event log (separate system) | Same database, same schema, same transaction |
| Provider independence | Locked to Keycloak APIs | Switch identity provider without touching authz |

The product DB is the sole source of truth for authorization. Keycloak handles
**authentication** (who are you?). The `members` table handles **authorization** (what can
you do?).

---

## The Migration

`backend/src/main/resources/db/migration/tenant/V2__create_members.sql`:

```sql
CREATE TABLE members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    role            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    first_login_at  TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_members_status ON members (status);
CREATE INDEX idx_members_email ON members (email);
```

This runs per-tenant schema via Flyway. The `UNIQUE` constraint on `keycloak_user_id`
guarantees one `Member` record per Keycloak user per tenant — idempotent by design.

---

## What's Next

We have members, roles, and the filter chain that bridges Keycloak identities into the tenant
schema. In [Post 07: Your First Domain Entity](./07-your-first-domain-entity.md), we'll use
the `Project` entity to walk through the canonical pattern every future entity in the template
follows — entity, repository, service, controller, migration, and integration test.

---

*This is post 6 of 10 in the **Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4** series.*
