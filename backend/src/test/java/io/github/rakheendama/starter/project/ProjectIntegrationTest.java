package io.github.rakheendama.starter.project;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.customer.Customer;
import io.github.rakheendama.starter.customer.CustomerRepository;
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
class ProjectIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private TenantProvisioningService tenantProvisioningService;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private JavaMailSender javaMailSender;

  private static final String OWNER_KC_USER_ID = "owner-kc-uuid";
  private static final String OWNER_EMAIL = "owner@test.com";

  private String orgSlug;
  private String schemaName;
  private UUID ownerMemberId;
  private UUID customerId;

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
              projectRepository.deleteAll();
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

              var customer =
                  new Customer("Test Customer", "customer@test.com", "Test Co", ownerMemberId);
              customer = customerRepository.save(customer);
              customerId = customer.getId();
            });
  }

  @Test
  void createProject_ownerCanCreate_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "New Project", "description": "A test project", "customerId": "%s" }
                    """
                        .formatted(customerId))
                .with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("New Project"))
        .andExpect(jsonPath("$.description").value("A test project"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.createdBy").value(ownerMemberId.toString()));
  }

  @Test
  void createProject_withoutCustomerId_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "No Customer Project", "description": "Missing customerId" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listProjects_returnsAllInTenant() throws Exception {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              projectRepository.save(
                  new Project("Project A", "Desc A", customerId, ownerMemberId));
              projectRepository.save(
                  new Project("Project B", "Desc B", customerId, ownerMemberId));
            });

    mockMvc
        .perform(get("/api/projects").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void getProject_includesCustomerInfo() throws Exception {
    UUID[] projectId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var project =
                  projectRepository.save(
                      new Project("Detail Project", "With details", customerId, ownerMemberId));
              projectId[0] = project.getId();
            });

    mockMvc
        .perform(get("/api/projects/{id}", projectId[0]).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Detail Project"))
        .andExpect(jsonPath("$.customerId").value(customerId.toString()));
  }

  @Test
  void updateProject_changesFields() throws Exception {
    UUID[] projectId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var project =
                  projectRepository.save(
                      new Project("Old Title", "Old desc", customerId, ownerMemberId));
              projectId[0] = project.getId();
            });

    mockMvc
        .perform(
            put("/api/projects/{id}", projectId[0])
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "title": "New Title", "description": "New description" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("New Title"))
        .andExpect(jsonPath("$.description").value("New description"));
  }

  @Test
  void changeStatus_activeToCompletedToArchived() throws Exception {
    UUID[] projectId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var project =
                  projectRepository.save(
                      new Project("Status Project", null, customerId, ownerMemberId));
              projectId[0] = project.getId();
            });

    // ACTIVE -> COMPLETED
    mockMvc
        .perform(
            patch("/api/projects/{id}/status", projectId[0])
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "status": "COMPLETED" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // COMPLETED -> ARCHIVED
    mockMvc
        .perform(
            patch("/api/projects/{id}/status", projectId[0])
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "status": "ARCHIVED" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void tenantIsolation_projectInTenantANotVisibleFromTenantB() throws Exception {
    // Seed a project in tenant A
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () ->
                projectRepository.save(
                    new Project("Tenant A Project", null, customerId, ownerMemberId)));

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

    // Request from tenant B should see 0 projects
    mockMvc
        .perform(
            get("/api/projects")
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
}
