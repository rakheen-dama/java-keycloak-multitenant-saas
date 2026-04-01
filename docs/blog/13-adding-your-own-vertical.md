---
title: "Building on the Template: Adding Your Own Vertical"
description: "A hands-on tutorial for adding industry-specific features to the template: new entities, custom fields, compliance checklists, and the patterns that keep vertical concerns separate from the foundation."
series: "Zero to Prod: Multitenant SaaS with Java 25, Keycloak & Spring Boot 4"
post: 13
---

# Building on the Template: Adding Your Own Vertical

You've cloned the template, started the dev stack, created your first tenant via the access request flow. Now what?

This post is a hands-on guide to adding your first industry-specific feature set. I'll use a **property management** vertical as the example — different from DocTeams' accounting and law firm verticals, to show the pattern is truly portable.

By the end, you'll have added: a new entity (Property), a custom field pack (property-specific attributes), a compliance checklist (tenant screening), and the wiring that makes them work within the existing multi-tenant foundation.

---

## Step 1: The Entity

Every vertical starts with a domain entity. For property management, the core entity is a Property — linked to a Customer (the property owner/landlord).

### Migration

Create `backend/src/main/resources/db/migration/tenant/V7__create_properties.sql`:

```sql
CREATE TABLE IF NOT EXISTS properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(100) NOT NULL,
    province VARCHAR(100),
    postal_code VARCHAR(20),
    property_type VARCHAR(50) NOT NULL DEFAULT 'RESIDENTIAL',
    bedrooms INTEGER,
    monthly_rental NUMERIC(12,2),
    status VARCHAR(20) NOT NULL DEFAULT 'VACANT',
    created_by UUID NOT NULL REFERENCES members(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_properties_customer_id ON properties (customer_id);
CREATE INDEX IF NOT EXISTS idx_properties_status ON properties (status);
```

This follows the template's migration conventions:
- `IF NOT EXISTS` for idempotency
- `gen_random_uuid()` for ID generation
- `REFERENCES` for foreign keys within the tenant schema
- No `tenant_id` column — schema boundary handles isolation

### Entity

Create `backend/src/main/java/.../property/Property.java`:

```java
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "city", nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PropertyStatus status;

    @Column(name = "monthly_rental")
    private BigDecimal monthlyRental;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructor, getters, business methods

    public Property(UUID customerId, String addressLine1, String city,
                    PropertyType propertyType, UUID createdBy) {
        this.customerId = customerId;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.propertyType = propertyType;
        this.status = PropertyStatus.VACANT;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
```

Notice: this is a **plain JPA entity**. No `@Filter`, no `tenant_id`, no `TenantAware` interface. The multi-tenancy is handled by the connection provider setting `search_path` — your entity doesn't know it's in a multi-tenant system.

### Repository, Service, Controller

These follow the exact patterns from Post 07 ("Your First Domain Entity"):

```java
// Repository — standard JpaRepository
public interface PropertyRepository extends JpaRepository<Property, UUID> {
    List<Property> findByCustomerId(UUID customerId);
    List<Property> findByStatus(PropertyStatus status);
}

// Service — one @Transactional method per operation
@Service
@Transactional(readOnly = true)
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final CustomerRepository customerRepository;

    @Transactional
    public Property create(UUID customerId, CreatePropertyRequest request) {
        customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        var property = new Property(
            customerId,
            request.addressLine1(),
            request.city(),
            PropertyType.valueOf(request.propertyType()),
            RequestScopes.requireMemberId());

        return propertyRepository.save(property);
    }
}

// Controller — one service call per endpoint
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    @PostMapping
    public ResponseEntity<PropertyResponse> create(
            @RequestParam UUID customerId,
            @Valid @RequestBody CreatePropertyRequest request) {
        var property = propertyService.create(customerId, request);
        return ResponseEntity.created(
            URI.create("/api/properties/" + property.getId()))
            .body(PropertyResponse.from(property));
    }
}
```

Run the build: `./mvnw clean verify -q`. The new migration runs against the test schema, the entity maps correctly, the controller serves requests.

That's it for the foundation. You now have a tenant-scoped Property entity with CRUD. Every tenant gets their own `properties` table in their own schema. No isolation code written.

---

## Step 2: The Integration Test

Before adding more features, prove the entity works in a multi-tenant context:

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PropertyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantProvisioningService provisioningService;

    private static final String ORG_ID = "org_property_test";

    @BeforeAll
    void provisionTenant() {
        provisioningService.provisionTenant(ORG_ID, "Property Test Org");
    }

    @Test
    void createProperty_returnsCreated() throws Exception {
        // First create a customer (prerequisite)
        var customerResult = mockMvc.perform(post("/api/customers")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "John Smith", "email": "john@example.com"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String customerId = JsonPath.read(
            customerResult.getResponse().getContentAsString(), "$.id");

        // Create a property linked to the customer
        mockMvc.perform(post("/api/properties")
                .with(ownerJwt())
                .param("customerId", customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "addressLine1": "42 Oak Avenue",
                      "city": "Cape Town",
                      "propertyType": "RESIDENTIAL"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.addressLine1").value("42 Oak Avenue"))
            .andExpect(jsonPath("$.status").value("VACANT"));
    }

    private JwtRequestPostProcessor ownerJwt() {
        return jwt().jwt(j -> j
            .subject("user_property_owner")
            .claim("o", Map.of("id", ORG_ID, "rol", "owner")));
    }
}
```

This test provisions a real tenant schema, runs real migrations, creates real records, and verifies tenant isolation — all with Testcontainers.

---

## Step 3: Custom Fields (Optional, Per-Vertical)

If you want to add fields that vary by property type or region without schema changes, use the JSONB `custom_fields` pattern from DocTeams.

Add a JSONB column to the properties table:

```sql
-- V8__add_custom_fields_to_properties.sql
ALTER TABLE properties ADD COLUMN IF NOT EXISTS
    custom_fields JSONB DEFAULT '{}';
```

And map it in the entity:

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "custom_fields", columnDefinition = "jsonb")
private Map<String, Object> customFields = new HashMap<>();
```

Now your property management vertical can store things like:
- `erf_number` (South African property registration number)
- `sectional_title_unit` (unit number for apartments)
- `body_corporate_levy` (monthly levy amount)
- `occupancy_certificate_expiry` (compliance date)

Without another migration. Without another column. The JSONB column holds whatever the vertical needs.

If you want structured validation and dropdown options for these fields (like DocTeams' field pack system), that's a product feature to build on top — but the storage pattern is ready.

---

## Step 4: Compliance Checklist (The Vertical Differentiator)

This is where your vertical becomes valuable. For property management in South Africa, tenant screening has regulatory requirements:

```
Tenant Screening Checklist:
☐ ID verification (certified copy)
☐ Proof of income (3 months payslips or bank statements)
☐ Credit check consent form signed
☐ Credit report obtained and reviewed
☐ Previous landlord reference contacted
☐ Employment verification letter
☐ FICA verification complete (if applicable)
```

You can build this as:
1. A `ChecklistTemplate` entity (reusable template)
2. A `ChecklistInstance` entity (created per customer/property)
3. A `ChecklistItem` entity (individual items with completion tracking)

Or, for a simpler start, use the Project entity as the "lease" and track checklist items as tasks. The template's Project + Comment structure already supports this — just rename "Project" to "Lease" and "Customer" to "Tenant" in the frontend.

This is the same approach DocTeams uses with terminology overrides. The data model stays generic. The language adapts.

---

## Step 5: The Frontend

The template ships with a Next.js 16 frontend that talks to the Gateway (not the backend directly). Adding a Properties page follows the existing patterns:

```
frontend/app/(app)/properties/page.tsx      — list view
frontend/app/(app)/properties/[id]/page.tsx — detail view
frontend/components/property/              — property-specific components
```

The frontend uses Server Components by default. Data fetching goes through the Gateway's proxied API:

```typescript
// Server Component — fetches via Gateway session cookie
async function PropertiesPage() {
  const properties = await api.get<Property[]>('/api/properties');

  return (
    <div>
      <h1>Properties</h1>
      <PropertyList properties={properties} />
    </div>
  );
}
```

The BFF pattern means no JWT handling in the frontend. `fetch('/api/properties')` sends the session cookie to the Gateway, which attaches the bearer token and forwards to the backend. Your frontend code doesn't know about OAuth2.

---

## The Pattern

The process for adding any vertical feature to this template is:

1. **Migration** — add a table in `db/migration/tenant/V{N}__create_{entity}.sql`
2. **Entity** — plain JPA, no multitenancy boilerplate
3. **Repository** — standard `JpaRepository`
4. **Service** — `@Transactional`, constructor injection, `RequestScopes` for current member
5. **Controller** — one service call per endpoint, `@Valid` for input
6. **Test** — Testcontainers + real PostgreSQL + real tenant schema
7. **Frontend** — Server Component + Gateway fetch

Each step builds on patterns that already exist in the template's codebase. The Customer and Project entities are your reference implementations. Copy the pattern, change the names, add your fields.

The multitenancy core, auth filters, provisioning pipeline, and Gateway BFF don't change. They're foundation. Your vertical features are the product you build on top.

---

*This post is part of the "Zero to Prod" series for the [java-keycloak-multitenant-saas](https://github.com/rakheen-dama/java-keycloak-multitenant-saas) template.*

*Previous: [Admin-Approved Registration](12-admin-approved-registration.md)*

*For the vertical pack system that DocTeams built on top of this foundation — compliance packs, field packs, terminology overrides — see the ["From Generic to Vertical"](/blog/series-3-generic-to-vertical/) series.*
