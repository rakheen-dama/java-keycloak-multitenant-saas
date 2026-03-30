package io.github.rakheendama.starter.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.exception.InvalidStateException;
import io.github.rakheendama.starter.exception.ResourceNotFoundException;
import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import io.github.rakheendama.starter.provisioning.ProvisioningResult;
import io.github.rakheendama.starter.provisioning.TenantProvisioningService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessRequestApprovalServiceTest {

  @Autowired private AccessRequestApprovalService approvalService;
  @Autowired private AccessRequestRepository accessRequestRepository;
  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private TenantProvisioningService tenantProvisioningService;
  @MockitoBean private JavaMailSender javaMailSender;

  private static final String ADMIN_USER_ID = "admin_001";
  private static final String KC_ORG_ID = "kc-org-123";

  @BeforeEach
  void setUp() {
    accessRequestRepository.deleteAll();
  }

  @Test
  void approve_pendingRequest_createsOrgAndProvisionsTenant() {
    var request = createPendingRequest("approve@acme.com", "Acme Corp");

    when(keycloakProvisioningClient.createOrganization("Acme Corp", "acme-corp"))
        .thenReturn(KC_ORG_ID);
    when(tenantProvisioningService.provisionTenant("acme-corp", "Acme Corp", KC_ORG_ID))
        .thenReturn(ProvisioningResult.success("tenant_acmeacme1234"));

    var result = approvalService.approve(request.getId(), ADMIN_USER_ID);

    assertThat(result.getStatus()).isEqualTo("APPROVED");
    verify(keycloakProvisioningClient).createOrganization("Acme Corp", "acme-corp");
    verify(tenantProvisioningService).provisionTenant("acme-corp", "Acme Corp", KC_ORG_ID);
    verify(keycloakProvisioningClient).inviteUser(KC_ORG_ID, "approve@acme.com");
  }

  @Test
  void approve_provisioningFails_savesErrorAndKeycloakOrgId() {
    var request = createPendingRequest("fail@acme.com", "Fail Corp");

    when(keycloakProvisioningClient.createOrganization("Fail Corp", "fail-corp"))
        .thenReturn(KC_ORG_ID);
    when(tenantProvisioningService.provisionTenant("fail-corp", "Fail Corp", KC_ORG_ID))
        .thenThrow(new RuntimeException("Schema creation failed"));

    assertThatThrownBy(() -> approvalService.approve(request.getId(), ADMIN_USER_ID))
        .isInstanceOf(RuntimeException.class);

    var saved = accessRequestRepository.findById(request.getId()).orElseThrow();
    assertThat(saved.getProvisioningError()).isEqualTo("Schema creation failed");
    assertThat(saved.getStatus()).isEqualTo("PENDING");
    assertThat(saved.getKeycloakOrgId()).isEqualTo(KC_ORG_ID);
    verify(keycloakProvisioningClient, never()).inviteUser(anyString(), anyString());
  }

  @Test
  void approve_retryAfterPartialFailure_skipsOrgCreation() {
    var request = createPendingRequest("retry@acme.com", "Retry Corp");
    request.setKeycloakOrgId(KC_ORG_ID);
    request.setProvisioningError("Schema creation failed");
    accessRequestRepository.save(request);

    when(tenantProvisioningService.provisionTenant("retry-corp", "Retry Corp", KC_ORG_ID))
        .thenReturn(ProvisioningResult.success("tenant_retryretry12"));

    var result = approvalService.approve(request.getId(), ADMIN_USER_ID);

    assertThat(result.getStatus()).isEqualTo("APPROVED");
    assertThat(result.getProvisioningError()).isNull();
    verify(keycloakProvisioningClient, never()).createOrganization(anyString(), anyString());
    verify(tenantProvisioningService).provisionTenant("retry-corp", "Retry Corp", KC_ORG_ID);
  }

  @Test
  void reject_pendingRequest_setsRejectedStatus() {
    var request = createPendingRequest("reject@acme.com", "Reject Corp");

    var result = approvalService.reject(request.getId(), ADMIN_USER_ID);

    assertThat(result.getStatus()).isEqualTo("REJECTED");
    assertThat(result.getReviewedBy()).isEqualTo(ADMIN_USER_ID);
    assertThat(result.getReviewedAt()).isNotNull();
  }

  @Test
  void approve_notFound_throwsResourceNotFoundException() {
    assertThatThrownBy(() -> approvalService.approve(UUID.randomUUID(), ADMIN_USER_ID))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void approve_alreadyApproved_throwsInvalidStateException() {
    var request = createPendingRequest("already@acme.com", "Already Corp");
    request.setStatus("APPROVED");
    accessRequestRepository.save(request);

    assertThatThrownBy(() -> approvalService.approve(request.getId(), ADMIN_USER_ID))
        .isInstanceOf(InvalidStateException.class);
  }

  private AccessRequest createPendingRequest(String email, String orgName) {
    var request = new AccessRequest(email, "Test User", orgName, "ZA", "Legal");
    request.setStatus("PENDING");
    return accessRequestRepository.save(request);
  }
}
