package io.github.rakheendama.starter.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
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
class MemberIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private MemberRepository memberRepository;
  @Autowired private MemberSyncService memberSyncService;
  @Autowired private TenantProvisioningService tenantProvisioningService;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private JavaMailSender javaMailSender;

  private static final String OWNER_KC_USER_ID = "owner-kc-uuid";
  private static final String MEMBER_KC_USER_ID = "member-kc-uuid";
  private static final String OWNER_EMAIL = "owner@test.com";
  private static final String MEMBER_EMAIL = "member@test.com";

  private String orgSlug;
  private String schemaName;
  private String kcOrgId;

  private UUID ownerMemberId;
  private UUID regularMemberId;

  @BeforeAll
  void provisionTenant() {
    orgSlug = "test-org-" + UUID.randomUUID().toString().substring(0, 8);
    kcOrgId = "kc-" + orgSlug;
    schemaName = SchemaNameGenerator.generate(orgSlug);
    tenantProvisioningService.provisionTenant(orgSlug, "Test Org", kcOrgId);
  }

  @BeforeEach
  void setUp() {
    // Clean up members in tenant schema
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(() -> memberRepository.deleteAll());

    // Create owner and member records
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var owner = new Member(OWNER_KC_USER_ID, OWNER_EMAIL, "Test Owner", "owner");
              owner = memberRepository.save(owner);
              ownerMemberId = owner.getId();

              var member = new Member(MEMBER_KC_USER_ID, MEMBER_EMAIL, "Test Member", "member");
              member = memberRepository.save(member);
              regularMemberId = member.getId();
            });
  }

  // --- T4.2: MemberSyncService tests ---

  @Test
  void firstLogin_createsOwnerMemberRecord() {
    when(keycloakProvisioningClient.isOrgCreator(kcOrgId, "new-creator-uuid")).thenReturn(true);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              // Delete all existing members first
              memberRepository.deleteAll();

              var jwt =
                  org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                      .header("alg", "RS256")
                      .subject("new-creator-uuid")
                      .claim("email", "creator@test.com")
                      .claim("name", "Creator")
                      .claim("organization", List.of(orgSlug))
                      .build();

              Member created = memberSyncService.syncOrCreate(jwt, kcOrgId);

              assertThat(created.getRole()).isEqualTo("owner");
              assertThat(created.getEmail()).isEqualTo("creator@test.com");
              assertThat(created.getFirstLoginAt()).isNotNull();
              assertThat(created.getLastLoginAt()).isNotNull();
            });
  }

  @Test
  void firstLogin_createsMemberRecord() {
    when(keycloakProvisioningClient.isOrgCreator(kcOrgId, "new-invited-uuid")).thenReturn(false);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var jwt =
                  org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                      .header("alg", "RS256")
                      .subject("new-invited-uuid")
                      .claim("email", "invited@test.com")
                      .claim("name", "Invited User")
                      .claim("organization", List.of(orgSlug))
                      .build();

              Member created = memberSyncService.syncOrCreate(jwt, kcOrgId);

              assertThat(created.getRole()).isEqualTo("member");
              assertThat(created.getEmail()).isEqualTo("invited@test.com");
            });
  }

  @Test
  void subsequentLogin_syncsIdentityNotRole() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              var jwt =
                  org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-token")
                      .header("alg", "RS256")
                      .subject(OWNER_KC_USER_ID)
                      .claim("email", "updated-owner@test.com")
                      .claim("name", "Updated Owner")
                      .claim("organization", List.of(orgSlug))
                      .build();

              Member synced = memberSyncService.syncOrCreate(jwt, kcOrgId);

              assertThat(synced.getEmail()).isEqualTo("updated-owner@test.com");
              assertThat(synced.getDisplayName()).isEqualTo("Updated Owner");
              assertThat(synced.getRole()).isEqualTo("owner"); // Role NOT changed
            });
  }

  // --- T4.5: MemberController API tests ---

  @Test
  void getMembers_returnsTenantMembers() throws Exception {
    mockMvc
        .perform(
            get("/api/members")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(OWNER_KC_USER_ID)
                                    .claim("email", OWNER_EMAIL)
                                    .claim("name", "Test Owner")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void getMe_returnsCurrentMember() throws Exception {
    mockMvc
        .perform(
            get("/api/members/me")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(OWNER_KC_USER_ID)
                                    .claim("email", OWNER_EMAIL)
                                    .claim("name", "Test Owner")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
        .andExpect(jsonPath("$.role").value("owner"));
  }

  @Test
  void inviteMember_ownerCanInvite() throws Exception {
    mockMvc
        .perform(
            post("/api/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "newuser@test.com" }
                    """)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(OWNER_KC_USER_ID)
                                    .claim("email", OWNER_EMAIL)
                                    .claim("name", "Test Owner")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isCreated());

    verify(keycloakProvisioningClient).inviteUser(anyString(), anyString());
  }

  @Test
  void inviteMember_memberCannotInvite() throws Exception {
    mockMvc
        .perform(
            post("/api/members/invite")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "email": "newuser@test.com" }
                    """)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(MEMBER_KC_USER_ID)
                                    .claim("email", MEMBER_EMAIL)
                                    .claim("name", "Test Member")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isForbidden());
  }

  @Test
  void changeRole_ownerCanChangeRole() throws Exception {
    mockMvc
        .perform(
            patch("/api/members/{id}/role", regularMemberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "role": "owner" }
                    """)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(OWNER_KC_USER_ID)
                                    .claim("email", OWNER_EMAIL)
                                    .claim("name", "Test Owner")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("owner"));
  }

  @Test
  void changeRole_memberCannotChangeRole() throws Exception {
    mockMvc
        .perform(
            patch("/api/members/{id}/role", ownerMemberId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "role": "member" }
                    """)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(MEMBER_KC_USER_ID)
                                    .claim("email", MEMBER_EMAIL)
                                    .claim("name", "Test Member")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isForbidden());
  }

  @Test
  void removeMember_ownerCanRemove() throws Exception {
    mockMvc
        .perform(
            delete("/api/members/{id}", regularMemberId)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(OWNER_KC_USER_ID)
                                    .claim("email", OWNER_EMAIL)
                                    .claim("name", "Test Owner")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isNoContent());

    // Verify member is gone
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(() -> assertThat(memberRepository.findById(regularMemberId)).isEmpty());
  }

  @Test
  void removeMember_memberCannotRemove() throws Exception {
    mockMvc
        .perform(
            delete("/api/members/{id}", ownerMemberId)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(MEMBER_KC_USER_ID)
                                    .claim("email", MEMBER_EMAIL)
                                    .claim("name", "Test Member")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isForbidden());
  }

  @Test
  void removeMember_cannotRemoveSelf() throws Exception {
    mockMvc
        .perform(
            delete("/api/members/{id}", ownerMemberId)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject(OWNER_KC_USER_ID)
                                    .claim("email", OWNER_EMAIL)
                                    .claim("name", "Test Owner")
                                    .claim("organization", List.of(orgSlug)))))
        .andExpect(status().isBadRequest());
  }
}
