package io.github.rakheendama.starter.accessrequest;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import io.github.rakheendama.starter.provisioning.ProvisioningResult;
import io.github.rakheendama.starter.provisioning.TenantProvisioningService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PlatformAdminIntegrationTest {

  private static final String BASE_PATH = "/api/platform-admin/access-requests";

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessRequestRepository accessRequestRepository;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private TenantProvisioningService tenantProvisioningService;
  @MockitoBean private JavaMailSender javaMailSender;

  @BeforeEach
  void setUp() {
    accessRequestRepository.deleteAll();
  }

  @Test
  void listRequests_nonAdmin_returns403() throws Exception {
    mockMvc
        .perform(get(BASE_PATH).with(jwt().jwt(j -> j.subject("user_regular"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void listRequests_platformAdmin_returnsAll() throws Exception {
    createPendingRequest("alice@example.com", "Alice Corp");
    createPendingRequest("bob@example.com", "Bob Inc");

    mockMvc
        .perform(
            get(BASE_PATH)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_admin")
                                    .claim("groups", List.of("platform-admins")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void approve_platformAdmin_returns200AndApproved() throws Exception {
    var request = createPendingRequest("approve@example.com", "Approve Corp");

    when(keycloakProvisioningClient.createOrganization(anyString(), anyString()))
        .thenReturn("kc-org-123");
    when(tenantProvisioningService.provisionTenant(anyString(), anyString(), anyString()))
        .thenReturn(ProvisioningResult.success("tenant_abc123def456"));

    mockMvc
        .perform(
            post(BASE_PATH + "/" + request.getId() + "/approve")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_admin")
                                    .claim("groups", List.of("platform-admins")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.reviewedBy").value("user_admin"));
  }

  @Test
  void reject_platformAdmin_returns200AndRejected() throws Exception {
    var request = createPendingRequest("reject@example.com", "Reject Corp");

    mockMvc
        .perform(
            post(BASE_PATH + "/" + request.getId() + "/reject")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_admin")
                                    .claim("groups", List.of("platform-admins")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.reviewedBy").value("user_admin"));
  }

  private AccessRequest createPendingRequest(String email, String orgName) {
    var request = new AccessRequest(email, "Test User", orgName, "ZA", "Legal");
    request.setStatus("PENDING");
    return accessRequestRepository.save(request);
  }
}
