---
title: "Portal Comments — Dual-Auth Writes"
description: "How team members and portal customers write to the same comment timeline: a single table with an author_type discriminator, two creation paths, one unified query, and the trade-offs of polymorphic author_id."
series: "Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4"
post: 10
---

# Portal Comments — Dual-Auth Writes

We have two completely separate authentication paths: Keycloak JWTs for team members and
portal JWTs for customers. Now we need both to write to the same table — a project comment
timeline where members and customers see each other's messages in chronological order.

The design challenge: **how do you model a single entity that has two different types of
author, authenticated by two different systems?**

The answer is a discriminator column, a polymorphic author ID, and a decision documented in
[ADR-T006: Dual-Author Comments via Discriminator](../adr/ADR-T006-dual-author-comments-via-discriminator.md).

---

## The Comment Entity

`backend/src/main/java/io/github/rakheendama/starter/comment/Comment.java`:

```java
@Entity
@Table(name = "comments")
public class Comment {

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "author_type", nullable = false, length = 20)
  private String authorType;       // "MEMBER" or "CUSTOMER"

  @Column(name = "author_id", nullable = false)
  private UUID authorId;           // polymorphic — references members.id OR customers.id

  @Column(name = "author_name", length = 255)
  private String authorName;       // denormalized for display

  protected Comment() {}

  /** Constructor for MEMBER comments. */
  public Comment(UUID projectId, String content, UUID memberId, String memberName) {
    this.projectId = projectId;
    this.content = content;
    this.authorType = "MEMBER";
    this.authorId = memberId;
    this.authorName = memberName;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Constructor for CUSTOMER comments. */
  public Comment(UUID projectId, String content, UUID customerId,
                 String customerName, String authorType) {
    this.projectId = projectId;
    this.content = content;
    this.authorType = authorType;   // "CUSTOMER"
    this.authorId = customerId;
    this.authorName = customerName;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }
}
```

Two constructors for two creation paths. The member constructor hardcodes `authorType` to
`"MEMBER"`. The customer constructor accepts it as a parameter (though it's always
`"CUSTOMER"` in practice) — this keeps the door open for future discriminator values
like `"SYSTEM"` without schema changes.

---

## Two Creation Paths

`backend/src/main/java/io/github/rakheendama/starter/comment/CommentService.java`:

### Member Path (Keycloak-authenticated)

```java
@Transactional
public Comment addMemberComment(UUID projectId, String content) {
  projectRepository.findById(projectId)
      .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
  UUID memberId = RequestScopes.requireMemberId();
  String memberName = memberRepository.findById(memberId)
      .map(Member::getDisplayName).orElse(null);
  var comment = new Comment(projectId, content, memberId, memberName);
  return commentRepository.save(comment);
}
```

The member's identity comes from `RequestScopes.MEMBER_ID`, bound by `MemberFilter` in the
Keycloak JWT filter chain. The display name is looked up from the `members` table and
denormalized into the comment.

### Customer Path (Portal-authenticated)

`backend/src/main/java/io/github/rakheendama/starter/portal/PortalController.java`:

```java
@PostMapping("/projects/{id}/comments")
public ResponseEntity<PortalCommentResponse> addComment(
    @PathVariable UUID id, @Valid @RequestBody AddCommentRequest request) {
  UUID customerId = RequestScopes.CUSTOMER_ID.get();
  findOwnedProject(id, customerId);  // ownership check — 404 if not theirs
  String customerName = customerRepository.findById(customerId)
      .map(Customer::getName).orElse(null);
  var comment = commentService.addCustomerComment(
      id, request.content(), customerId, customerName);
  return ResponseEntity.created(URI.create("/api/portal/projects/" + id + "/comments"))
      .body(PortalCommentResponse.from(comment));
}
```

```java
// In CommentService
@Transactional
public Comment addCustomerComment(UUID projectId, String content,
                                   UUID customerId, String customerName) {
  projectRepository.findById(projectId)
      .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
  var comment = new Comment(projectId, content, customerId, customerName, "CUSTOMER");
  return commentRepository.save(comment);
}
```

The customer's identity comes from `RequestScopes.CUSTOMER_ID`, bound by `PortalAuthFilter`
in the portal JWT filter chain. The ownership check (`findOwnedProject`) ensures a customer
can only comment on their own projects.

> **The key insight:** Both paths end at the same `commentRepository.save()`. The Comment
> entity doesn't know or care which authentication system created it. The discriminator
> (`author_type`) records the origin; the polymorphic `author_id` points to the right table.

---

## One Unified Timeline

```java
public interface CommentRepository extends JpaRepository<Comment, UUID> {
  List<Comment> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
}
```

A single query returns all comments on a project — member comments and customer comments
interleaved in chronological order. No UNION. No separate queries. No merge logic.

The `author_type` field drives UI rendering: a `"MEMBER"` comment gets a team badge, a
`"CUSTOMER"` comment gets a customer badge. The `author_name` denormalization means the UI
doesn't need to join to either `members` or `customers` to display who wrote each comment.

---

## The Migration

`backend/src/main/resources/db/migration/tenant/V5__create_comments.sql`:

```sql
CREATE TABLE comments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    content     TEXT NOT NULL,
    author_type VARCHAR(20) NOT NULL CHECK (author_type IN ('MEMBER', 'CUSTOMER')),
    author_id   UUID NOT NULL,
    author_name VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_project_id ON comments (project_id, created_at ASC);
CREATE INDEX idx_comments_author ON comments (author_type, author_id);
```

The `CHECK` constraint on `author_type` enforces the discriminator values at the database
level. The composite index on `(project_id, created_at ASC)` supports the
`findByProjectIdOrderByCreatedAtAsc` query efficiently.

---

## Why a Single Table

The alternative was two tables — `member_comments` and `customer_comments` — with identical
schemas. Here's why we didn't:

| Concern | Two Tables | Single Table |
|---------|-----------|-------------|
| Timeline query | UNION + ORDER BY | Single SELECT + ORDER BY |
| Repository interfaces | Two repos, same methods | One repo |
| Service methods | Duplicated | Shared (with discriminator) |
| Controller endpoints | Duplicated | Shared (with auth-path dispatch) |
| Adding a third author type | New table, repo, service, controller | New discriminator value |

> **The trade-off:** `author_id` has no foreign key constraint. It can't — it references
> either `members.id` or `customers.id` depending on `author_type`, and PostgreSQL doesn't
> support conditional FKs. Referential integrity is enforced by application code: both
> `addMemberComment` and `addCustomerComment` look up the author before creating the comment.

For a template, simplicity wins. One table, one repo, one query, one timeline.

---

## Deletion: Only Your Own

```java
@Transactional
public void deleteMemberComment(UUID commentId) {
  var comment = commentRepository.findById(commentId)
      .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));
  UUID callerId = RequestScopes.requireMemberId();
  if (!"MEMBER".equals(comment.getAuthorType()) || !callerId.equals(comment.getAuthorId())) {
    throw new ForbiddenException("Cannot delete comment",
        "You can only delete your own comments");
  }
  commentRepository.delete(comment);
}
```

Members can only delete their own comments. The check is two-part: the comment must have
`authorType = "MEMBER"` AND the `authorId` must match the current member. A member cannot
delete a customer's comment, even if they're an owner.

---

## The Series Wrap-Up

Over ten posts, we've built a production-shaped multitenant SaaS template:

1. **Architecture & Schema-Per-Tenant** — why schemas over row-level filtering
2. **One-Command Dev Environment** — Docker Compose, Testcontainers, everything local
3. **The Multitenancy Core** — ScopedValues, filters, Hibernate schema routing
4. **Spring Cloud Gateway as BFF** — OAuth2 sessions, token relay, CSRF at the edge
5. **Tenant Registration Pipeline** — OTP, admin approval, idempotent provisioning
6. **Members, Roles & Profile Sync** — Keycloak identities to tenant members
7. **Your First Domain Entity** — the canonical pattern for entities, services, tests
8. **Security Hardening** — schema validation, token design, anti-enumeration
9. **The Magic Link Portal** — separate auth path for customers, no Keycloak dependency
10. **Portal Comments** — dual-auth writes to a unified timeline

The template is open source. Fork it, extend it, ship it.

---

*This is post 10 of 10 in the **Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4** series.*
