package io.github.rakheendama.starter.accessrequest;

import io.github.rakheendama.starter.exception.InvalidStateException;
import io.github.rakheendama.starter.exception.ResourceNotFoundException;
import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import io.github.rakheendama.starter.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccessRequestApprovalService {

  private static final Logger log = LoggerFactory.getLogger(AccessRequestApprovalService.class);

  private final AccessRequestRepository accessRequestRepository;
  private final @Nullable KeycloakProvisioningClient keycloakProvisioningClient;
  private final TenantProvisioningService tenantProvisioningService;
  private final TransactionTemplate txTemplate;

  public AccessRequestApprovalService(
      AccessRequestRepository accessRequestRepository,
      @Nullable KeycloakProvisioningClient keycloakProvisioningClient,
      TenantProvisioningService tenantProvisioningService,
      TransactionTemplate txTemplate) {
    this.accessRequestRepository = accessRequestRepository;
    this.keycloakProvisioningClient = keycloakProvisioningClient;
    this.tenantProvisioningService = tenantProvisioningService;
    this.txTemplate = txTemplate;
  }

  public AccessRequest approve(UUID requestId, String adminEmail) {
    if (keycloakProvisioningClient == null) {
      throw new IllegalStateException(
          "Keycloak admin client not configured — set keycloak.admin.auth-server-url");
    }

    // Step 1: Validate and load (short transaction)
    var request = txTemplate.execute(tx -> findPendingRequest(requestId));

    String orgName = request.getOrganizationName();
    String slug = slugify(orgName);
    String email = request.getEmail();

    try {
      // Step 2: Create KC org if needed, persist kcOrgId immediately
      String kcOrgId = request.getKeycloakOrgId();
      if (kcOrgId == null) {
        kcOrgId = keycloakProvisioningClient.createOrganization(orgName, slug);
        log.info("Created Keycloak organization '{}' with ID {}", orgName, kcOrgId);
        final String orgId = kcOrgId;
        txTemplate.executeWithoutResult(
            tx -> {
              var fresh = accessRequestRepository.findById(requestId).orElseThrow();
              fresh.setKeycloakOrgId(orgId);
              accessRequestRepository.save(fresh);
            });
      } else {
        log.info(
            "Keycloak org {} already exists for request {}, skipping creation",
            kcOrgId,
            requestId);
      }

      // Step 3: Provision tenant schema (NO outer transaction)
      tenantProvisioningService.provisionTenant(slug, orgName, kcOrgId);
      log.info("Provisioned tenant schema for org {} (slug={})", kcOrgId, slug);

      // Step 4: Invite user and set org creator
      keycloakProvisioningClient.inviteUser(kcOrgId, email);
      keycloakProvisioningClient.setOrgCreator(kcOrgId, email);
      log.info("Sent invitation to {} for org {}", email, kcOrgId);

      // Step 5: Mark approved (short transaction, re-fetch for current @Version)
      return txTemplate.execute(
          tx -> {
            var fresh = accessRequestRepository.findById(requestId).orElseThrow();
            fresh.setStatus("APPROVED");
            fresh.setReviewedBy(adminEmail);
            fresh.setReviewedAt(Instant.now());
            fresh.setProvisioningError(null);
            return accessRequestRepository.save(fresh);
          });
    } catch (Exception e) {
      log.error("Approval failed for request {}: {}", requestId, e.getMessage(), e);
      String rawMsg = e.getMessage();
      String errorMsg =
          (rawMsg != null && rawMsg.length() > 500) ? rawMsg.substring(0, 500) : rawMsg;
      // Persist error in separate TX so it always commits (re-fetch for current @Version)
      txTemplate.executeWithoutResult(
          tx -> {
            var fresh = accessRequestRepository.findById(requestId).orElseThrow();
            fresh.setProvisioningError(errorMsg);
            accessRequestRepository.save(fresh);
          });
      throw e;
    }
  }

  @Transactional
  public AccessRequest reject(UUID requestId, String adminEmail) {
    var request = findPendingRequest(requestId);
    request.setStatus("REJECTED");
    request.setReviewedBy(adminEmail);
    request.setReviewedAt(Instant.now());
    return accessRequestRepository.save(request);
  }

  @Transactional(readOnly = true)
  public List<AccessRequest> listRequests(@Nullable String statusFilter) {
    if (statusFilter != null) {
      return accessRequestRepository.findByStatus(statusFilter);
    }
    return accessRequestRepository.findAllByOrderByCreatedAtDesc();
  }

  private AccessRequest findPendingRequest(UUID requestId) {
    var request =
        accessRequestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("AccessRequest", requestId));
    if (!"PENDING".equals(request.getStatus())) {
      throw new InvalidStateException(
          "Access request not pending",
          "Access request " + requestId + " has status " + request.getStatus());
    }
    return request;
  }

  private String slugify(String input) {
    return input
        .toLowerCase()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("[\\s]+", "-")
        .replaceAll("-{2,}", "-")
        .replaceAll("^-|-$", "");
  }
}
