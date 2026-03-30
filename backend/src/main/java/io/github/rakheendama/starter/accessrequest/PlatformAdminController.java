package io.github.rakheendama.starter.accessrequest;

import io.github.rakheendama.starter.security.PlatformSecurityService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform-admin/access-requests")
public class PlatformAdminController {

  private final AccessRequestApprovalService approvalService;
  private final PlatformSecurityService platformSecurityService;

  public PlatformAdminController(
      AccessRequestApprovalService approvalService,
      PlatformSecurityService platformSecurityService) {
    this.approvalService = approvalService;
    this.platformSecurityService = platformSecurityService;
  }

  @GetMapping
  public ResponseEntity<List<AccessRequestResponse>> listRequests(
      @RequestParam(value = "status", required = false) String status,
      JwtAuthenticationToken auth) {
    platformSecurityService.requirePlatformAdmin(auth.getToken());
    return ResponseEntity.ok(
        approvalService.listRequests(status).stream().map(AccessRequestResponse::from).toList());
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<AccessRequestResponse> approve(
      @PathVariable UUID id, JwtAuthenticationToken auth) {
    platformSecurityService.requirePlatformAdmin(auth.getToken());
    return ResponseEntity.ok(
        AccessRequestResponse.from(approvalService.approve(id, auth.getToken().getSubject())));
  }

  @PostMapping("/{id}/reject")
  public ResponseEntity<AccessRequestResponse> reject(
      @PathVariable UUID id, JwtAuthenticationToken auth) {
    platformSecurityService.requirePlatformAdmin(auth.getToken());
    return ResponseEntity.ok(
        AccessRequestResponse.from(approvalService.reject(id, auth.getToken().getSubject())));
  }

  public record AccessRequestResponse(
      UUID id,
      String email,
      String fullName,
      String organizationName,
      String country,
      String industry,
      String status,
      String keycloakOrgId,
      String provisioningError,
      String reviewedBy,
      Instant reviewedAt,
      Instant createdAt,
      Instant updatedAt) {

    public static AccessRequestResponse from(AccessRequest r) {
      return new AccessRequestResponse(
          r.getId(),
          r.getEmail(),
          r.getFullName(),
          r.getOrganizationName(),
          r.getCountry(),
          r.getIndustry(),
          r.getStatus(),
          r.getKeycloakOrgId(),
          r.getProvisioningError(),
          r.getReviewedBy(),
          r.getReviewedAt(),
          r.getCreatedAt(),
          r.getUpdatedAt());
    }
  }
}
