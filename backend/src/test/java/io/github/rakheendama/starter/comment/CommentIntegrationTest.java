package io.github.rakheendama.starter.comment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
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
class CommentIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CommentRepository commentRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private TenantProvisioningService tenantProvisioningService;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private JavaMailSender javaMailSender;

  private static final String OWNER_KC_USER_ID = "comment-owner-kc-uuid";
  private static final String OWNER_EMAIL = "comment-owner@test.com";
  private static final String MEMBER_KC_USER_ID = "comment-member-kc-uuid";
  private static final String MEMBER_EMAIL = "comment-member@test.com";

  private String orgSlug;
  private String schemaName;
  private UUID ownerMemberId;
  private UUID memberMemberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void provisionTenant() {
    orgSlug = "test-comment-" + UUID.randomUUID().toString().substring(0, 8);
    String kcOrgId = "kc-" + orgSlug;
    schemaName = SchemaNameGenerator.generate(orgSlug);
    tenantProvisioningService.provisionTenant(orgSlug, "Test Comment Org", kcOrgId);
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
            });

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var owner = new Member(OWNER_KC_USER_ID, OWNER_EMAIL, "Test Owner", "owner");
              owner = memberRepository.save(owner);
              ownerMemberId = owner.getId();

              var member = new Member(MEMBER_KC_USER_ID, MEMBER_EMAIL, "Test Member", "member");
              member = memberRepository.save(member);
              memberMemberId = member.getId();

              var customer =
                  new Customer("Test Customer", "customer@test.com", "Test Co", ownerMemberId);
              customer = customerRepository.save(customer);
              customerId = customer.getId();

              var project =
                  new Project("Test Project", "For comments", customerId, ownerMemberId);
              project = projectRepository.save(project);
              projectId = project.getId();
            });
  }

  @Test
  void addMemberComment_ownerCanAdd_returns201() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/{projectId}/comments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "content": "This is a test comment" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value("This is a test comment"))
        .andExpect(jsonPath("$.authorType").value("MEMBER"))
        .andExpect(jsonPath("$.authorId").value(ownerMemberId.toString()))
        .andExpect(jsonPath("$.projectId").value(projectId.toString()))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void listComments_returnsChronologicalOrder() throws Exception {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              commentRepository.save(
                  new Comment(projectId, "First comment", ownerMemberId, "Test Owner"));
              commentRepository.save(
                  new Comment(projectId, "Second comment", ownerMemberId, "Test Owner"));
            });

    mockMvc
        .perform(get("/api/projects/{projectId}/comments", projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].content").value("First comment"))
        .andExpect(jsonPath("$[1].content").value("Second comment"));
  }

  @Test
  void deleteOwnComment_returns204() throws Exception {
    UUID[] commentId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var comment =
                  commentRepository.save(
                      new Comment(projectId, "To be deleted", ownerMemberId, "Test Owner"));
              commentId[0] = comment.getId();
            });

    mockMvc
        .perform(delete("/api/comments/{id}", commentId[0]).with(ownerJwt()))
        .andExpect(status().isNoContent());

    // Verify it's actually gone
    mockMvc
        .perform(get("/api/projects/{projectId}/comments", projectId).with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void cannotDeleteAnotherMembersComment_returns403() throws Exception {
    UUID[] commentId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var comment =
                  commentRepository.save(
                      new Comment(projectId, "Owner's comment", ownerMemberId, "Test Owner"));
              commentId[0] = comment.getId();
            });

    // Member tries to delete owner's comment
    mockMvc
        .perform(delete("/api/comments/{id}", commentId[0]).with(memberJwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void listComments_nonExistentProject_returns404() throws Exception {
    UUID fakeProjectId = UUID.randomUUID();
    mockMvc
        .perform(get("/api/projects/{projectId}/comments", fakeProjectId).with(ownerJwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void addMemberComment_authorNameDenormalized() throws Exception {
    mockMvc
        .perform(
            post("/api/projects/{projectId}/comments", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "content": "Check author name" }
                    """)
                .with(ownerJwt()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.authorName").value("Test Owner"));
  }

  @Test
  void tenantIsolation_commentsInTenantANotVisibleFromTenantB() throws Exception {
    // Seed a comment in tenant A
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () ->
                commentRepository.save(
                    new Comment(projectId, "Tenant A comment", ownerMemberId, "Test Owner")));

    // Provision tenant B
    String orgSlugB = "test-comment-b-" + UUID.randomUUID().toString().substring(0, 8);
    String kcOrgIdB = "kc-" + orgSlugB;
    String schemaBName = SchemaNameGenerator.generate(orgSlugB);
    tenantProvisioningService.provisionTenant(orgSlugB, "Test Org B", kcOrgIdB);

    // Seed owner + customer + project in tenant B
    String ownerBKcId = "owner-b-" + UUID.randomUUID();
    UUID[] projectBId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaBName)
        .where(RequestScopes.ORG_ID, orgSlugB)
        .run(
            () -> {
              var ownerB =
                  memberRepository.save(
                      new Member(ownerBKcId, "ownerb@test.com", "Owner B", "owner"));
              var customerB =
                  customerRepository.save(
                      new Customer("Cust B", "custb@test.com", "Co B", ownerB.getId()));
              var projectB =
                  projectRepository.save(
                      new Project("Project B", null, customerB.getId(), ownerB.getId()));
              projectBId[0] = projectB.getId();
            });

    // Request comments in tenant B project should return 0
    mockMvc
        .perform(
            get("/api/projects/{projectId}/comments", projectBId[0])
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

  @Test
  void cascadingDelete_deleteProjectRemovesComments() throws Exception {
    // Seed a separate project with comments for cascade test
    UUID[] cascadeProjectId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var cascadeProject =
                  projectRepository.save(
                      new Project("Cascade Project", null, customerId, ownerMemberId));
              cascadeProjectId[0] = cascadeProject.getId();
              commentRepository.save(
                  new Comment(
                      cascadeProject.getId(), "Will be cascaded", ownerMemberId, "Test Owner"));
            });

    // Delete the project via repository (simulates cascade)
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(() -> projectRepository.deleteById(cascadeProjectId[0]));

    // Listing comments for deleted project should return 404 (project not found)
    mockMvc
        .perform(
            get("/api/projects/{projectId}/comments", cascadeProjectId[0]).with(ownerJwt()))
        .andExpect(status().isNotFound());
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
