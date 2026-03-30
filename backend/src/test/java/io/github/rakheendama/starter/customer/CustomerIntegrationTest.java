package io.github.rakheendama.starter.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.member.Member;
import io.github.rakheendama.starter.member.MemberRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.multitenancy.SchemaNameGenerator;
import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import io.github.rakheendama.starter.provisioning.TenantProvisioningService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private TenantProvisioningService tenantProvisioningService;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private JavaMailSender javaMailSender;

  private static final String OWNER_KC_USER_ID = "owner-kc-uuid";
  private static final String OWNER_EMAIL = "owner@test.com";
  private static final String MEMBER_KC_USER_ID = "member-kc-uuid";
  private static final String MEMBER_EMAIL = "member@test.com";

  private String orgSlug;
  private String schemaName;
  private UUID ownerMemberId;

  @BeforeAll
  void provisionTenant() {
    orgSlug = "test-org-" + UUID.randomUUID().toString().substring(0, 8);
    String kcOrgId = "kc-" + orgSlug;
    schemaName = SchemaNameGenerator.generate(orgSlug);
    tenantProvisioningService.provisionTenant(orgSlug, "Test Org", kcOrgId);
  }

  @BeforeEach
  void setUp() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              customerRepository.deleteAll();
              memberRepository.deleteAll();
            });

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var owner = new Member(OWNER_KC_USER_ID, OWNER_EMAIL, "Test Owner", "owner");
              owner = memberRepository.save(owner);
              ownerMemberId = owner.getId();

              memberRepository.save(
                  new Member(MEMBER_KC_USER_ID, MEMBER_EMAIL, "Test Member", "member"));
            });
  }

  @Test
  void createCustomer_ownerCanCreate_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "Acme Corp", "email": "contact@acme.com", "company": "Acme Inc" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Acme Corp"))
        .andExpect(jsonPath("$.email").value("contact@acme.com"))
        .andExpect(jsonPath("$.company").value("Acme Inc"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.createdBy").value(ownerMemberId.toString()));
  }

  @Test
  void listCustomers_returnsAllInTenant() throws Exception {
    // Seed two customers
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              customerRepository.save(
                  new Customer("Customer A", "a@test.com", null, ownerMemberId));
              customerRepository.save(
                  new Customer("Customer B", "b@test.com", "B Corp", ownerMemberId));
            });

    mockMvc
        .perform(get("/api/customers").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void getCustomer_byId_returnsCustomer() throws Exception {
    UUID[] customerId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var customer =
                  customerRepository.save(
                      new Customer("Test Customer", "test@example.com", "Test Co", ownerMemberId));
              customerId[0] = customer.getId();
            });

    mockMvc
        .perform(get("/api/customers/{id}", customerId[0]).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Test Customer"))
        .andExpect(jsonPath("$.email").value("test@example.com"))
        .andExpect(jsonPath("$.company").value("Test Co"));
  }

  @Test
  void updateCustomer_changesFields() throws Exception {
    UUID[] customerId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var customer =
                  customerRepository.save(
                      new Customer("Old Name", "old@test.com", null, ownerMemberId));
              customerId[0] = customer.getId();
            });

    mockMvc
        .perform(
            put("/api/customers/{id}", customerId[0])
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "New Name", "email": "new@test.com", "company": "New Corp" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("New Name"))
        .andExpect(jsonPath("$.email").value("new@test.com"))
        .andExpect(jsonPath("$.company").value("New Corp"));
  }

  @Test
  void archiveCustomer_setsStatusArchived() throws Exception {
    UUID[] customerId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var customer =
                  customerRepository.save(
                      new Customer("To Archive", "archive@test.com", null, ownerMemberId));
              customerId[0] = customer.getId();
            });

    mockMvc
        .perform(delete("/api/customers/{id}", customerId[0]).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void createCustomer_memberRole_returns403() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "name": "Blocked Corp", "email": "blocked@acme.com", "company": "Blocked Inc" }
                    """)
                .with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void tenantIsolation_customerInTenantANotVisibleFromTenantB() throws Exception {
    // Seed a customer in tenant A
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () ->
                customerRepository.save(
                    new Customer("Tenant A Customer", "a@test.com", null, ownerMemberId)));

    // Provision tenant B
    String orgSlugB = "test-org-b-" + UUID.randomUUID().toString().substring(0, 8);
    String kcOrgIdB = "kc-" + orgSlugB;
    String schemaBName = SchemaNameGenerator.generate(orgSlugB);
    tenantProvisioningService.provisionTenant(orgSlugB, "Test Org B", kcOrgIdB);

    // Seed an owner member in tenant B
    String ownerBKcId = "owner-b-" + UUID.randomUUID();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaBName)
        .where(RequestScopes.ORG_ID, orgSlugB)
        .run(
            () ->
                memberRepository.save(
                    new Member(ownerBKcId, "ownerb@test.com", "Owner B", "owner")));

    // Request from tenant B should see 0 customers
    mockMvc
        .perform(
            get("/api/customers")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(ownerBKcId)
                                    .claim("email", "ownerb@test.com")
                                    .claim("name", "Owner B")
                                    .claim("organization", List.of(orgSlugB)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .JwtRequestPostProcessor
      ownerJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject(OWNER_KC_USER_ID)
                    .claim("email", OWNER_EMAIL)
                    .claim("name", "Test Owner")
                    .claim("organization", List.of(orgSlug)));
  }

  private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
          .JwtRequestPostProcessor
      memberJwt() {
    return jwt()
        .jwt(
            j ->
                j.subject(MEMBER_KC_USER_ID)
                    .claim("email", MEMBER_EMAIL)
                    .claim("name", "Test Member")
                    .claim("organization", List.of(orgSlug)));
  }
}
