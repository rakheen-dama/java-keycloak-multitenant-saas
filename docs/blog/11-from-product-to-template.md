---
title: "From Product to Template: What I Kept, What I Cut, and Why"
description: "How I extracted a reusable multitenant SaaS template from a 240K-line production codebase: the 8 entities that matter, the 75 that don't, and the decisions behind each cut."
series: "Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4"
post: 11
---

# From Product to Template: What I Kept, What I Cut, and Why

DocTeams is a multi-tenant B2B SaaS platform with 83 entities, 104 controllers, 155 services, and 99 database migrations. It took 843 pull requests over 8 weeks to build.

This template has 8 entities, 9 controllers, and 8 migrations. It took 22 pull requests.

The interesting question isn't what's in the template — it's what I removed and why. Every cut was a decision about what's *foundational* (must get right before any feature work) versus what's *domain-specific* (belongs in the product, not the starting point).

---

## The Extraction Criteria

I asked one question for each DocTeams feature: **"Would a developer building a different B2B SaaS product need this on day one?"**

If the answer was "yes, and getting it wrong is expensive to fix later" — it stayed. If the answer was "yes, but they can add it when they need it" — it was cut.

The result:

| Kept | Cut | Reasoning |
|------|-----|-----------|
| Schema-per-tenant isolation | Row-Level Security path | RLS was removed from DocTeams itself (Phase 13). Don't ship complexity we already deleted. |
| ScopedValue request context | ThreadLocal alternative | ScopedValue is the right answer for virtual threads. No reason to include the old pattern. |
| Spring Cloud Gateway BFF | Direct SPA-to-API auth | Token-in-browser is a security risk. BFF is the correct pattern for B2B. |
| Keycloak organizations | Clerk integration | Template is self-hosted. No proprietary auth dependency. |
| Gated registration | Self-serve signup | B2B needs approval. Self-serve is a consumer pattern. |
| Member sync from Keycloak | Complex RBAC (capabilities, modules) | Two roles (owner/member) are enough to start. Capability system is a product concern. |
| Customer entity | Customer lifecycle state machine | The state machine is valuable but domain-specific. Template ships with a simple customer. |
| Project entity | Tasks, time entries, invoices, rates, budgets | These are DocTeams' domain. A consulting tool or law firm tool would have different entities. |
| Comments (dual-auth) | Notifications, activity feeds, audit logs | Comments demonstrate the dual-auth pattern. The rest are features, not patterns. |
| Magic link portal | Full portal with read-model sync | Magic links show the pattern. The read-model sync is an optimization for DocTeams' scale. |
| Idempotent provisioning | Vertical pack seeding | Provisioning is foundational. What gets seeded is product-specific. |
| Flyway dual-path migrations | 99 migration files | 8 migrations demonstrate the pattern. 99 are DocTeams' schema. |
| Testcontainers setup | 455 test files | 18 tests prove the patterns work. 455 test the product. |

## What "Foundational" Means

The 8 entities in the template aren't just "simple CRUD." Each one demonstrates a pattern that every tenant of the template will need:

**`AccessRequest`** (public schema) — demonstrates the gated registration flow: form submission, OTP email verification, admin approval. The state machine (PENDING_VERIFICATION → PENDING → APPROVED/REJECTED) is the pattern. Your product might collect different fields, but the flow is the same.

**`OrgSchemaMapping`** (public schema) — the lookup table that maps org identifiers to tenant schema names. This is the foundation of tenant resolution. Every request passes through it.

**`Organization`** (tenant schema) — the tenant identity within their own schema. Tracks provisioning status.

**`Member`** (tenant schema) — demonstrates Keycloak identity sync. First login creates the member. Subsequent logins update profile. Invitations flow through Keycloak's org invitation API. Roles live in the product database, not in Keycloak.

**`Customer`** (tenant schema) — the first "business entity" pattern. Shows how to build tenant-scoped CRUD with `@RequiresCapability` authorization. A consulting firm would rename this to "Client." A property manager would rename it to "Tenant" (confusingly). The pattern is the same.

**`Project`** (tenant schema) — the second business entity, linked to Customer via foreign key. Demonstrates cross-entity relationships within a tenant schema.

**`Comment`** (tenant schema) — the dual-auth pattern. Both team members and portal customers can create comments on the same entity. The `author_type` discriminator (`MEMBER` or `CUSTOMER`) determines which auth path created the record. This is the hardest pattern to get right and the one most developers would skip.

**`MagicLinkToken`** (public schema) — cryptographic magic link tokens for portal access. SHA-256 hashed storage, single-use exchange, expiry handling. Demonstrates passwordless auth without Keycloak dependency.

## What I Deliberately Left Out

### Billing and Subscriptions

DocTeams has subscription management, plan enforcement, and feature gating. The template has none.

Why: billing models vary wildly between products. Stripe, Paddle, LemonSqueezy, self-hosted — the choice depends on market, pricing model, and payment provider availability (Stripe isn't available in all countries). Shipping an opinionated billing integration would lock template users into a choice before they understand their market.

### File Storage (S3)

DocTeams has presigned URL upload/download with org-scoped S3 key paths and LocalStack for local development.

Why: not every B2B SaaS needs file storage on day one. A time-tracking SaaS, a scheduling tool, or a CRM might never need it. When they do, the pattern is well-documented (Post 5 of the "Modern Java for SaaS" blog series covers it). Including S3 would add LocalStack to the dev stack, AWS SDK to dependencies, and configuration to every environment — complexity for a feature that's optional.

### Audit Trail

DocTeams has an `AuditEvent` entity with domain event publishing, activity feeds, and structured logging.

Why: audit requirements are compliance-driven and vary by industry. An accounting firm's audit needs (FICA verification tracking, SARS submission records) differ from a healthcare SaaS's needs (HIPAA access logs). The template includes the `ApplicationEventPublisher` pattern in the services — adding an audit listener is straightforward when you know what to audit.

### Vertical Pack System

DocTeams has compliance packs, field packs, template packs, clause packs, and automation packs — all seeded per industry vertical.

Why: the pack system is the product's competitive advantage, not a foundation pattern. The template provisions empty tenant schemas. What gets seeded into them is entirely the product developer's decision.

### Complex RBAC

DocTeams has a capability registry with 20+ capabilities, module-gated endpoints, and custom org roles.

Why: two roles (owner and member) cover 90% of early-stage B2B products. The template demonstrates the pattern (roles in the product database, not in Keycloak). Upgrading to capability-based RBAC when you need it is a natural evolution — and the architecture supports it because roles already live in the app.

## The Numbers

| Metric | DocTeams | Template | Ratio |
|--------|----------|----------|-------|
| Entities | 83 | 8 | 10:1 |
| Controllers | 104 | 9 | 12:1 |
| Services | 155 | ~12 | 13:1 |
| Migrations | 99 | 8 | 12:1 |
| Test files | 455 | 18 | 25:1 |
| Java LOC | 240,000 | ~15,000 | 16:1 |
| ADRs | 87+ | 7 | 12:1 |
| Blog posts | 29 (DocTeams series) | 10 | 3:1 |

The template is roughly 1/15th the size of DocTeams. That's not a measure of quality — it's a measure of focus. The template asks: "What patterns must be correct before you write your first feature?" DocTeams answers: "What does an accounting firm need to run their practice?"

## The Development Approach

The template wasn't extracted by deleting files from DocTeams. It was built from scratch, using DocTeams as the reference implementation. Each slice had a scout agent that read the relevant DocTeams code, produced a brief, and a builder that implemented the template's version.

Why rebuild instead of extract? Because extraction carries baggage. DocTeams has naming artifacts from the Clerk era (`clerk_org_id` column names), patterns that evolved through 13 phases of refactoring, and conventions that assume 83 entities exist. A clean build produces a template that's internally consistent — every pattern is intentional, not vestigial.

The 7 ADRs in the template are also fresh — they reference DocTeams' ADRs for context but make their own decisions. ADR-T004 (Gateway BFF) references DocTeams' gateway but explains the decision from first principles, not from "we did it this way in DocTeams."

## How to Use the Template

The intended workflow:

1. **Clone the template.** Rename the base package from `io.github.rakheendama.starter` to your own.
2. **Run `dev-up.sh`.** PostgreSQL, Keycloak, and Mailpit start. Keycloak realm is imported automatically.
3. **Start backend + gateway.** Two `mvnw spring-boot:run` commands.
4. **Use the access request flow** to create your first tenant.
5. **Add your first domain entity** following the Project pattern (Post 07 in this series walks through it).
6. **Add your vertical features** — the foundation handles multi-tenancy, auth, and provisioning.

You don't modify the multitenancy core, the filter chain, or the provisioning pipeline. You add entities, services, and controllers in new packages. The template's patterns propagate into your code through the reference examples.

---

*This post is part of the "Zero to Prod" series for the [java-keycloak-multitenant-saas](https://github.com/rakheen-dama/java-keycloak-multitenant-saas) template. For the story behind the product that inspired it, see ["One Dev, 843 PRs"](/blog/series-1-one-dev-843-prs/).*
