---
title: "Admin-Approved Registration: Why Self-Serve Sign-Up is Wrong for B2B"
description: "The case for gated tenant registration in B2B SaaS: access request state machines, OTP email verification, platform admin approval queues, and why 'just let them sign up' is a consumer pattern."
series: "Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4"
post: 12
---

# Admin-Approved Registration: Why Self-Serve Sign-Up is Wrong for B2B

Every SaaS tutorial starts the same way: "Add a sign-up page." User enters email and password, clicks a button, account created, redirect to dashboard. Simple. Fast. Scales to millions.

And completely wrong for B2B.

When a business signs up for your SaaS, you're not creating a user account — you're provisioning infrastructure. A PostgreSQL schema with 8+ tables. A Keycloak organization with its own member management. Flyway migrations. Seed data. Resources that cost money to create and maintain.

Self-serve sign-up means anyone — competitors, bots, spam accounts, curious students — can trigger this provisioning at will. For a B2B SaaS targeting professional services firms, that's not a feature. It's a liability.

---

## The Template's Approach: Gated Registration

This template implements a four-step registration flow:

```
Visitor → Access Request Form → OTP Email Verification → Admin Queue → Provisioning
```

No instant sign-up. No "your account is ready in 30 seconds." Instead: a human reviews every request before infrastructure is provisioned.

### Step 1: Access Request Form

The visitor fills out a form with their business details:

```
POST /api/access-requests
{
  "orgName": "Thornton & Associates",
  "contactName": "Alice Thornton",
  "contactEmail": "alice@thornton.co.za",
  "industry": "accounting"
}
```

This creates an `AccessRequest` record in the public schema with status `PENDING_VERIFICATION`. No schema created. No Keycloak org. No resources consumed beyond a single database row.

### Step 2: OTP Email Verification

The system sends a 6-digit OTP to the provided email via Mailpit (or your production SMTP):

```
Subject: Your verification code
Body: Your code is 847291. It expires in 10 minutes.
```

The visitor enters the code:

```
POST /api/access-requests/verify
{
  "email": "alice@thornton.co.za",
  "otp": "847291"
}
```

If correct, the request transitions to `PENDING`. If wrong, the attempt counter increments. After 5 failures, the request is locked.

Why OTP before admin review? Two reasons:
1. **Spam filtering.** Bots can fill forms. Bots can't receive and enter email OTPs (without effort). The OTP step eliminates 95% of junk requests before a human has to look at them.
2. **Email validation.** The admin needs to know the email is real before approving. If the email bounces, the OTP never arrives, the request stays in `PENDING_VERIFICATION`, and the admin never sees it.

### Step 3: Platform Admin Queue

Verified requests appear in the platform admin dashboard:

```
GET /api/platform-admin/access-requests?status=PENDING

[
  {
    "id": "...",
    "orgName": "Thornton & Associates",
    "contactName": "Alice Thornton",
    "contactEmail": "alice@thornton.co.za",
    "industry": "accounting",
    "status": "PENDING",
    "verifiedAt": "2026-03-15T10:23:00Z"
  }
]
```

The admin reviews the request and either approves or rejects:

```
POST /api/platform-admin/access-requests/{id}/approve
POST /api/platform-admin/access-requests/{id}/reject
```

Approval triggers provisioning. Rejection sends a notification email (if configured).

### Step 4: Provisioning

On approval, the idempotent provisioning pipeline runs:

1. Create Keycloak organization (using the admin CLI service account)
2. Create the owner user in Keycloak, add to organization
3. Generate schema name: `tenant_` + SHA-256(orgSlug)[0:12]
4. `CREATE SCHEMA IF NOT EXISTS "tenant_abc123"`
5. Run Flyway tenant migrations (8 migration files)
6. Create `org_schema_mapping` entry (last — so TenantFilter only sees complete schemas)
7. Send welcome email with login instructions

The pipeline is idempotent at every step. If it fails at step 5 (say, a database connection timeout), the retry skips steps 1-4 (already done) and resumes at step 5.

---

## Why Not Self-Serve?

The arguments for self-serve sign-up are real: lower friction, faster growth, no bottleneck on an admin's availability. Here's why they don't apply to B2B SaaS at early stage:

### 1. Every tenant costs real resources

A PostgreSQL schema with 8 tables, indexes, and constraints isn't free. Multiply by 100 spam sign-ups and you've got 800 tables in your database that nobody uses. Flyway startup iterates every schema — 100 junk schemas add 10+ seconds to boot time.

With gated registration, only approved tenants exist. Your schema count equals your customer count.

### 2. You want to know your customers

At early stage, every new tenant is a learning opportunity. What industry are they in? What drew them to your product? What's their team size? The access request form captures this. Self-serve sign-up captures an email address.

The `industry` field on the access request is how the template knows which vertical profile to apply. Without it, every tenant gets generic defaults.

### 3. The admin delay is a feature, not a bug

"But users will leave if they can't sign up instantly!" — this is true for consumer products. For B2B, the buying process already involves demos, procurement reviews, and contract negotiations. A 24-hour approval delay is nothing compared to a 3-week procurement cycle.

The delay also creates a touch point. When Alice from Thornton & Associates submits a request, the admin can email her personally: "I saw your request — I'll have your account ready by tomorrow. What kind of engagements does your firm handle?" That's a sales conversation, not a friction point.

### 4. Anti-abuse without rate limiting complexity

Self-serve sign-up requires anti-abuse measures: CAPTCHAs (user-hostile), rate limiting (complex to tune), email domain blocklists (maintenance burden), and idle-tenant cleanup (operational risk).

Gated registration replaces all of this with one human check. The admin looks at the request and decides: legitimate or not. No algorithm needed.

---

## The State Machine

The `AccessRequest` entity uses an explicit state machine:

```java
public enum AccessRequestStatus {
    PENDING_VERIFICATION,  // Form submitted, OTP sent
    PENDING,               // OTP verified, waiting for admin
    APPROVED,              // Admin approved, provisioning triggered
    REJECTED               // Admin rejected
}
```

Transitions are enforced in the entity:

```java
public void verify() {
    if (this.status != AccessRequestStatus.PENDING_VERIFICATION) {
        throw new InvalidStateException(
            "Cannot verify", "Request is not pending verification");
    }
    this.status = AccessRequestStatus.PENDING;
    this.verifiedAt = Instant.now();
}

public void approve(String approvedBy) {
    if (this.status != AccessRequestStatus.PENDING) {
        throw new InvalidStateException(
            "Cannot approve", "Request is not pending");
    }
    this.status = AccessRequestStatus.APPROVED;
    this.approvedBy = approvedBy;
    this.approvedAt = Instant.now();
}
```

Invalid transitions throw `InvalidStateException` (rendered as 400 Bad Request with ProblemDetail). You can't approve a request that hasn't been verified. You can't verify a request that's already approved. The state machine makes invalid states unrepresentable.

---

## The OTP Implementation

The OTP system is deliberately simple — no TOTP, no authenticator apps, just a 6-digit code sent by email:

```java
// Generate cryptographically random 6-digit OTP
String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

// Store hashed (never store OTPs in plain text)
accessRequest.setOtpHash(hashOtp(otp));
accessRequest.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
accessRequest.setOtpAttempts(0);

// Send via email
emailService.sendOtp(accessRequest.getContactEmail(), otp);
```

Security properties:
- **Hashed storage.** OTPs are stored as SHA-256 hashes. A database breach doesn't reveal pending OTPs.
- **Time-limited.** 10-minute expiry. After that, the visitor must request a new code.
- **Attempt-limited.** 5 incorrect attempts locks the request. Prevents brute-force (1M combinations / 5 attempts = no chance).
- **Single-use.** Once verified, the OTP hash is cleared. Can't replay.

---

## The Platform Admin Endpoint

Platform admin access is determined by Keycloak group membership, not by tenant roles:

```java
@RestController
@RequestMapping("/api/platform-admin")
public class PlatformAdminController {

    @GetMapping("/access-requests")
    public List<AccessRequestResponse> listPending() {
        platformAdminGuard.requirePlatformAdmin(); // Checks Keycloak "platform-admins" group
        return accessRequestService.listPending();
    }

    @PostMapping("/access-requests/{id}/approve")
    public AccessRequestResponse approve(@PathVariable UUID id) {
        platformAdminGuard.requirePlatformAdmin();
        return accessRequestService.approve(id);
    }
}
```

`PlatformAdminGuard` reads the `groups` claim from the JWT (mapped by Keycloak's group claim mapper). If the user belongs to the `platform-admins` group, they can access these endpoints. This is separate from tenant-level roles — a platform admin manages the *platform*, not any specific tenant.

---

## When to Add Self-Serve

Gated registration is the right default. But there are scenarios where you'd add a self-serve path:

- **Freemium model.** If you offer a free tier that doesn't cost real resources (maybe a shared-schema tier with RLS), self-serve for free signups and gated for paid tiers.
- **High-volume market.** If you're targeting thousands of small businesses (e.g., freelancers, micro-agencies), the admin bottleneck becomes real. Consider auto-approval with human review after provisioning.
- **Demo/sandbox environments.** Short-lived, pre-seeded tenants that auto-destroy after 14 days. Self-serve for demos, gated for production tenants.

The template's architecture supports all of these. The provisioning pipeline is called from the approval endpoint — but it could equally be called from a webhook, a scheduled job, or a self-serve sign-up handler. The pipeline doesn't care who triggers it.

---

*This post is part of the "Zero to Prod" series for the [java-keycloak-multitenant-saas](https://github.com/rakheen-dama/java-keycloak-multitenant-saas) template.*

*Previous: [From Product to Template](11-from-product-to-template.md)*

*Next: [Building on the Template: Adding Your Own Vertical](13-adding-your-own-vertical.md)*
