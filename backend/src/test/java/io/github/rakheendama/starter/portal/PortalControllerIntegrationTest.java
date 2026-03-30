package io.github.rakheendama.starter.portal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.comment.Comment;
import io.github.rakheendama.starter.comment.CommentRepository;
import io.github.rakheendama.starter.customer.Customer;
import io.github.rakheendama.starter.customer.CustomerRepository;
import io.github.rakheendama.starter.member.Member;
import io.github.rakheendama.starter.member.MemberRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.multitenancy.SchemaNameGenerator;
import io.github.rakheendama.starter.project.Project;
import io.github.rakheendama.starter.project.ProjectRepository;
import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import io.github.rakheendama.starter.provisioning.TenantProvisioningService;
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
class PortalControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CommentRepository commentRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService tenantProvisioningService;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private JavaMailSender javaMailSender;

  private String orgSlug;
  private String schemaName;
  private UUID customerAId;
  private UUID customerBId;
  private UUID projectAId;
  private UUID memberId;
  private String customerAJwt;
  private String customerBJwt;

  @BeforeAll
  void provisionTenant() {
    orgSlug = "portal-test-" + UUID.randomUUID().toString().substring(0, 8);
    schemaName = SchemaNameGenerator.generate(orgSlug);
    tenantProvisioningService.provisionTenant(orgSlug, "Portal Test Org", "kc-" + orgSlug);
  }

  @BeforeEach
  void setUp() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              commentRepository.deleteAll();
              projectRepository.deleteAll();
              customerRepository.deleteAll();
              memberRepository.deleteAll();

              var customerA = new Customer("Customer A", "a@test.com", "Company A", null);
              customerA = customerRepository.save(customerA);
              customerAId = customerA.getId();

              var customerB = new Customer("Customer B", "b@test.com", "Company B", null);
              customerB = customerRepository.save(customerB);
              customerBId = customerB.getId();

              var project = new Project("Project Alpha", "Description", customerAId, null);
              project = projectRepository.save(project);
              projectAId = project.getId();

              var member = new Member("kc-user-1", "member@test.com", "Test Member", "member");
              member = memberRepository.save(member);
              memberId = member.getId();
            });

    customerAJwt = portalJwtService.issueToken(customerAId, orgSlug);
    customerBJwt = portalJwtService.issueToken(customerBId, orgSlug);
  }

  @Test
  void listProjects_returnsOnlyCustomerAProjects() throws Exception {
    mockMvc
        .perform(get("/api/portal/projects").header("Authorization", "Bearer " + customerAJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(projectAId.toString()))
        .andExpect(jsonPath("$[0].title").value("Project Alpha"));
  }

  @Test
  void listProjects_customerBSeesEmpty() throws Exception {
    mockMvc
        .perform(get("/api/portal/projects").header("Authorization", "Bearer " + customerBJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void projectDetail_ownProject_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/portal/projects/{id}", projectAId)
                .header("Authorization", "Bearer " + customerAJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(projectAId.toString()))
        .andExpect(jsonPath("$.title").value("Project Alpha"))
        .andExpect(jsonPath("$.customerId").value(customerAId.toString()));
  }

  @Test
  void projectDetail_anotherCustomersProject_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/portal/projects/{id}", projectAId)
                .header("Authorization", "Bearer " + customerBJwt))
        .andExpect(status().isNotFound());
  }

  @Test
  void listComments_ownProject_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/portal/projects/{id}/comments", projectAId)
                .header("Authorization", "Bearer " + customerAJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void addCustomerComment_returns201WithCustomerAuthorType() throws Exception {
    mockMvc
        .perform(
            post("/api/portal/projects/{id}/comments", projectAId)
                .header("Authorization", "Bearer " + customerAJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "content": "Hello from the customer portal" }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.authorType").value("CUSTOMER"))
        .andExpect(jsonPath("$.authorId").value(customerAId.toString()))
        .andExpect(jsonPath("$.authorName").value("Customer A"))
        .andExpect(jsonPath("$.content").value("Hello from the customer portal"));
  }

  @Test
  void customerCommentAppearsAlongsideMemberComment() throws Exception {
    // Seed a MEMBER comment directly
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              commentRepository.save(
                  new Comment(projectAId, "Member comment", memberId, "Test Member"));
            });

    // Add a CUSTOMER comment via POST
    mockMvc
        .perform(
            post("/api/portal/projects/{id}/comments", projectAId)
                .header("Authorization", "Bearer " + customerAJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "content": "Customer comment" }
                    """))
        .andExpect(status().isCreated());

    // List should return both with correct authorType values
    mockMvc
        .perform(
            get("/api/portal/projects/{id}/comments", projectAId)
                .header("Authorization", "Bearer " + customerAJwt))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].authorType").value("MEMBER"))
        .andExpect(jsonPath("$[1].authorType").value("CUSTOMER"));
  }

  @Test
  void portalJwtForCustomerACannotSeeCustomerBProject() throws Exception {
    // Create a project for customer B
    UUID projectBId;
    final UUID[] holder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var projectB = new Project("Project Beta", "B's project", customerBId, null);
              projectB = projectRepository.save(projectB);
              holder[0] = projectB.getId();
            });
    projectBId = holder[0];

    // Customer A should NOT see customer B's project — 404, not 403
    mockMvc
        .perform(
            get("/api/portal/projects/{id}", projectBId)
                .header("Authorization", "Bearer " + customerAJwt))
        .andExpect(status().isNotFound());
  }
}
