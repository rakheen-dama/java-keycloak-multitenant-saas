---
title: "E-Signing Documents from a Customer Portal — Without a Third-Party Service"
description: "How to build token-based document acceptance with PDF viewing, typed-name signatures, SHA-256 integrity hashing, and certificate generation — no DocuSign, no HelloSign, just your own backend."
series: "Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4"
post: 14
---

# E-Signing Documents from a Customer Portal — Without a Third-Party Service

DocuSign charges $10-25 per user per month. HelloSign charges $15-45. For a B2B SaaS where firms send engagement letters, service agreements, or proposals to clients, that cost multiplies across every tenant.

But here's the thing: most B2B "e-signing" doesn't need the full complexity of a third-party service. You don't need wet signature capture, multi-party signing workflows, or notarization. You need: show a document, let the recipient type their name, record the acceptance with a timestamp and IP address, and generate a certificate proving it happened.

That's what DocTeams built. No third-party service. No per-signature fees. Token-based access, a simple acceptance form, and a Certificate of Acceptance with a SHA-256 hash of the original document.

Here's how to build it.

---

## The Flow

```
Firm sends acceptance request
        │
        ▼
Email with unique token URL → recipient
        │
        ▼
Recipient clicks link → acceptance page (no login required)
        │
        ├── Views the PDF (embedded viewer)
        │
        ├── Types their full name
        │
        └── Clicks "I Accept"
                │
                ▼
        System records: name, IP, user-agent, timestamp
                │
                ▼
        Certificate of Acceptance generated (PDF)
        with SHA-256 hash of original document
                │
                ▼
        Firm notified → certificate downloadable
```

No authentication. No account creation. No password. The recipient clicks a link, reads the document, types their name, done. The token in the URL *is* the authentication — it proves the recipient received the email.

---

## The Acceptance Request Entity

The core data model:

```java
@Entity
@Table(name = "acceptance_requests")
public class AcceptanceRequest {

    @Id
    private UUID id;

    // What document is being accepted
    @Column(name = "document_reference", nullable = false)
    private UUID documentReference;

    // Who should accept it
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_name")
    private String recipientName;

    // The unique token for URL access
    @Column(name = "request_token", nullable = false, unique = true)
    private String requestToken;

    // Lifecycle
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AcceptanceStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Acceptance evidence (captured at signing)
    @Column(name = "acceptor_name")
    private String acceptorName;

    @Column(name = "acceptor_ip_address", length = 45)
    private String acceptorIpAddress;

    @Column(name = "acceptor_user_agent", length = 500)
    private String acceptorUserAgent;

    // Certificate (generated after acceptance)
    @Column(name = "certificate_storage_key")
    private String certificateStorageKey;

    // Reminders
    @Column(name = "reminder_count", nullable = false)
    private int reminderCount;

    @Column(name = "last_reminded_at")
    private Instant lastRemindedAt;
}
```

The entity is deliberately generic — it references a document by UUID and a recipient by email. It doesn't know what kind of document it is (engagement letter, service agreement, NDA). It doesn't know what system stores the document (S3, database, filesystem). It just tracks the acceptance lifecycle.

## The State Machine

```
PENDING → SENT → VIEWED → ACCEPTED
                    ↓
                 EXPIRED
     ↓
  REVOKED
```

```java
public enum AcceptanceStatus {
    PENDING,    // Created, not yet emailed
    SENT,       // Email sent to recipient
    VIEWED,     // Recipient opened the link (first view recorded)
    ACCEPTED,   // Recipient typed name and confirmed
    EXPIRED,    // Past expiry date without acceptance
    REVOKED     // Firm revoked the request
}
```

Transitions are enforced in the entity:

```java
public void markSent() {
    requireStatus(PENDING, "send");
    this.status = SENT;
    this.sentAt = Instant.now();
}

public void markViewed() {
    if (this.viewedAt == null) {
        this.viewedAt = Instant.now();
    }
    if (this.status == SENT) {
        this.status = VIEWED;
    }
}

public void markAccepted(String name, String ipAddress, String userAgent) {
    requireActiveStatus("accept");
    this.status = ACCEPTED;
    this.acceptorName = name;
    this.acceptorIpAddress = ipAddress;
    this.acceptorUserAgent = userAgent;
    this.acceptedAt = Instant.now();
}

private void requireActiveStatus(String action) {
    if (this.status == ACCEPTED || this.status == EXPIRED || this.status == REVOKED) {
        throw new InvalidStateException(
            "Cannot " + action, "Request is " + this.status);
    }
    if (Instant.now().isAfter(this.expiresAt)) {
        this.status = EXPIRED;
        throw new InvalidStateException(
            "Cannot " + action, "Request has expired");
    }
}
```

The `markViewed()` transition is special: it's non-destructive. If the recipient opens the link multiple times, only the first view is recorded. The status only changes from SENT to VIEWED (not from VIEWED to VIEWED). This gives the firm a signal: "the client has seen the document but hasn't accepted yet."

---

## Token Generation and Security

The token is the only authentication for the acceptance page. It must be:

- **Unguessable**: 256 bits of entropy (32 random bytes, base64-encoded to 43 characters)
- **Single-purpose**: One token per acceptance request, not reused
- **Time-limited**: Configurable expiry (default 30 days)

```java
private String generateToken() {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
}
```

The token goes in the URL: `https://portal.example.com/accept/dGhpc0lzQVRlc3RUb2tlbldpdGhFbm91Z2hFbnRyb3B5...`

**Why not hash the token?** DocTeams stores the token in plaintext because:
1. It's transmitted in the email URL anyway (the recipient has it)
2. It's single-use for acceptance (viewing is idempotent)
3. Expiry limits the attack window

If you're storing tokens for longer-lived resources (API keys, password reset tokens), you'd hash them. For 30-day acceptance tokens, the expiry is the primary defense.

**Why not use the portal magic-link system?** The template's magic links have a different lifecycle: they're consumed on first use (to prevent replay), they create a session (portal JWT), and they expire in 24 hours. Acceptance tokens need to survive multiple views, don't create sessions, and last 30 days. Separate systems for separate concerns.

---

## The Portal Acceptance Endpoints

Three endpoints, no authentication required (the token IS the auth):

```java
@RestController
@RequestMapping("/api/portal/acceptance")
public class PortalAcceptanceController {

    // View the acceptance page data (document info, status, org branding)
    @GetMapping("/{token}")
    public ResponseEntity<AcceptancePageData> getPageData(
            @PathVariable String token,
            HttpServletRequest request) {
        String ipAddress = resolveClientIp(request);
        return ResponseEntity.ok(acceptanceService.getPageData(token, ipAddress));
    }

    // Stream the PDF for the embedded viewer
    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> streamPdf(@PathVariable String token) {
        var download = acceptanceService.streamPdf(token);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"" + download.fileName() + "\"")
            .body(download.bytes());
    }

    // Accept the document
    @PostMapping("/{token}/accept")
    public ResponseEntity<AcceptanceResponse> accept(
            @PathVariable String token,
            @Valid @RequestBody AcceptanceSubmission submission,
            HttpServletRequest request) {
        return ResponseEntity.ok(acceptanceService.accept(
            token,
            submission,
            resolveClientIp(request),
            request.getHeader("User-Agent")));
    }
}
```

The `getPageData` endpoint also records the "viewed" timestamp — the first time the recipient opens the link, the request transitions from SENT to VIEWED. Subsequent views are no-ops.

The `accept` endpoint captures three pieces of evidence:
- **Acceptor name**: typed by the recipient (compared against recipient name on file)
- **IP address**: for audit trail
- **User-agent**: for audit trail

This evidence goes into the Certificate of Acceptance.

---

## The Acceptance Page (Frontend)

The portal page is simple — it doesn't need authentication, routing, or complex state:

```tsx
export function AcceptancePage({ token }: { token: string }) {
    const [pageData, setPageData] = useState<PageData | null>(null);
    const [name, setName] = useState("");
    const [status, setStatus] = useState<"loading" | "ready" | "accepted" | "error">("loading");

    useEffect(() => {
        fetch(`/api/portal/acceptance/${token}`)
            .then(res => res.json())
            .then(data => {
                setPageData(data);
                setStatus(data.status === "ACCEPTED" ? "accepted" : "ready");
            })
            .catch(() => setStatus("error"));
    }, [token]);

    async function handleAccept() {
        const res = await fetch(`/api/portal/acceptance/${token}/accept`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: name.trim() }),
        });
        if (res.ok) setStatus("accepted");
    }

    if (status === "loading") return <LoadingSpinner />;
    if (status === "error") return <ErrorState />;
    if (status === "accepted") return <AcceptedConfirmation data={pageData} />;

    return (
        <div className="max-w-3xl mx-auto p-6">
            {/* Organization branding */}
            <header className="flex items-center gap-4 mb-8">
                {pageData.orgLogo && <img src={pageData.orgLogo} alt="" className="h-10" />}
                <h1 className="text-xl font-semibold">{pageData.orgName}</h1>
            </header>

            {/* Document info */}
            <p className="text-muted-foreground mb-4">
                {pageData.orgName} has sent you a document for your review and acceptance.
            </p>
            <h2 className="text-lg font-medium mb-4">{pageData.documentTitle}</h2>

            {/* Embedded PDF viewer */}
            <iframe
                src={`/api/portal/acceptance/${token}/pdf`}
                className="w-full h-[600px] border rounded-lg mb-6"
                title="Document"
            />

            {/* Acceptance form */}
            <div className="border rounded-lg p-6 bg-muted/30">
                <p className="text-sm text-muted-foreground mb-4">
                    By typing your full name below and clicking "I Accept," you confirm
                    that you have read and agree to the terms of this document.
                </p>
                <label className="block text-sm font-medium mb-2">
                    Type your full name to accept
                </label>
                <input
                    type="text"
                    value={name}
                    onChange={e => setName(e.target.value)}
                    placeholder={pageData.recipientName}
                    className="w-full border rounded px-3 py-2 mb-4"
                />
                <button
                    onClick={handleAccept}
                    disabled={name.trim().length < 2}
                    className="w-full bg-primary text-primary-foreground rounded py-2 font-medium
                               disabled:opacity-50"
                >
                    I Accept
                </button>
            </div>

            {/* Expiry notice */}
            <p className="text-xs text-muted-foreground mt-4 text-center">
                This request expires on {new Date(pageData.expiresAt).toLocaleDateString()}.
            </p>
        </div>
    );
}
```

No login page. No session management. No auth library. The token in the URL grants access. The page loads, shows the PDF, collects the name, and submits.

---

## The Certificate of Acceptance

After acceptance, the system generates a Certificate of Acceptance — a PDF that serves as legal evidence the document was accepted.

The certificate contains:

```
CERTIFICATE OF ACCEPTANCE

Document: Engagement Letter — Monthly Bookkeeping
Accepted by: Alice Thornton
Date: 15 March 2026, 14:23 SAST
IP Address: 196.21.xxx.xxx
User Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)...

Document Integrity:
SHA-256: a3f2b1c4d5e6f7890123456789abcdef0123456789abcdef0123456789abcdef

This certificate confirms that the above-named individual accepted the referenced
document via the [OrgName] client portal. The SHA-256 hash uniquely identifies the
document version that was presented and accepted.
```

The SHA-256 hash is the key security feature. It's computed from the original PDF bytes at acceptance time:

```java
public void generateCertificate(AcceptanceRequest request) {
    // Download the original document
    byte[] originalPdf = storageService.download(request.getDocumentStorageKey());

    // Compute SHA-256 hash
    String documentHash = sha256Hex(originalPdf);

    // Build certificate context
    var context = new CertificateContext(
        request.getAcceptorName(),
        request.getAcceptedAt(),
        request.getAcceptorIpAddress(),
        request.getAcceptorUserAgent(),
        documentHash,
        documentTitle,
        orgName);

    // Render certificate HTML → PDF
    String html = certificateRenderer.render(context);
    byte[] certificatePdf = pdfService.htmlToPdf(html);

    // Store certificate
    String storageKey = storageService.upload(
        "certificates/" + request.getId() + ".pdf",
        certificatePdf,
        "application/pdf");

    request.setCertificateStorageKey(storageKey);
}

private String sha256Hex(byte[] data) {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
    return HexFormat.of().formatHex(hash);
}
```

**Why the hash matters**: If someone later claims "that's not the document I signed," you can re-hash the stored document and compare. If the hashes match, the document hasn't been modified since acceptance. If they don't, the stored document was tampered with. This provides non-repudiation without a third-party notarization service.

---

## Reminders and Revocation

### Reminders

The firm can send reminders for pending requests:

```java
public AcceptanceRequest remind(UUID requestId) {
    var request = findById(requestId);
    request.requireActiveStatus("remind");
    request.incrementReminderCount();

    notificationService.sendReminderEmail(request);

    return acceptanceRequestRepository.save(request);
}
```

Reminder count is tracked to prevent spam — the UI can limit reminders (e.g., max 3).

### Revocation

The firm can revoke a pending request (e.g., document was updated, wrong version sent):

```java
public AcceptanceRequest revoke(UUID requestId, UUID revokedBy) {
    var request = findById(requestId);
    request.markRevoked(revokedBy);
    // Optionally notify recipient that the request was cancelled
    return acceptanceRequestRepository.save(request);
}
```

After revocation, the acceptance page shows "This request has been cancelled" instead of the document.

### Auto-Revocation

When a firm sends a new acceptance request for the same document to the same recipient, the system automatically revokes any existing active request. This prevents duplicate acceptances:

```java
// Auto-revoke existing active request for same document + recipient
acceptanceRequestRepository
    .findByDocumentReferenceAndRecipientEmailAndStatusIn(
        documentId, recipientEmail,
        List.of(PENDING, SENT, VIEWED))
    .ifPresent(existing -> existing.markRevoked(sentBy));
```

---

## What This Isn't

This is not a replacement for DocuSign or HelloSign in all scenarios. It doesn't handle:

- **Wet signature capture** (drawing a signature on a touchscreen)
- **Multi-party signing** (multiple signers on the same document)
- **Signing order enforcement** (signer A must sign before signer B)
- **Legal compliance** for jurisdictions that require specific e-signature standards (eIDAS Advanced/Qualified, US ESIGN Act with specific audit requirements)
- **Biometric identity verification**

For B2B service agreements between a firm and their client — engagement letters, NDAs, service agreements, proposals — typed-name acceptance with IP logging and document hashing is legally sufficient in most jurisdictions and practically sufficient in all of them.

If you need the full complexity of multi-party, ordered, wet-signature e-signing, use DocuSign. If you need "client reads document, types name, acceptance is recorded" — build this. It's a few hundred lines of code and zero recurring costs.

---

*This post is part of the "Zero to Prod" series for the [java-keycloak-multitenant-saas](https://github.com/rakheen-dama/java-keycloak-multitenant-saas) template.*

*Previous: [Adding Your Own Vertical](13-adding-your-own-vertical.md)*

*For the production implementation with org branding, Thymeleaf certificates, and S3 storage, see [DocTeams](https://github.com/rakheen-dama/b2b-strawman). For the customer lifecycle that feeds into acceptance workflows, see ["The Customer Lifecycle State Machine"](/blog/series-2-multi-tenant-from-scratch/07-customer-lifecycle-state-machine.md).*
