package io.github.rakheendama.starter.member;

import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import io.github.rakheendama.starter.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberSyncService {

  private static final Logger log = LoggerFactory.getLogger(MemberSyncService.class);

  private final MemberRepository memberRepository;
  private final KeycloakProvisioningClient keycloakProvisioningClient;

  public MemberSyncService(
      MemberRepository memberRepository, KeycloakProvisioningClient keycloakProvisioningClient) {
    this.memberRepository = memberRepository;
    this.keycloakProvisioningClient = keycloakProvisioningClient;
  }

  @Transactional
  public Member syncOrCreate(Jwt jwt, String keycloakOrgId) {
    String keycloakUserId = JwtUtils.extractSub(jwt);
    String rawEmail = JwtUtils.extractEmail(jwt);
    String email = rawEmail != null ? rawEmail : keycloakUserId + "@unknown";
    String displayName = JwtUtils.extractName(jwt);

    return memberRepository
        .findByKeycloakUserId(keycloakUserId)
        .map(
            existing -> {
              existing.syncProfile(email, displayName);
              return memberRepository.save(existing);
            })
        .orElseGet(
            () -> {
              String role = determineRole(keycloakOrgId, keycloakUserId);
              log.info(
                  "Creating member for Keycloak user '{}' with role '{}'", keycloakUserId, role);
              var member = new Member(keycloakUserId, email, displayName, role);
              return memberRepository.save(member);
            });
  }

  private String determineRole(String keycloakOrgId, String keycloakUserId) {
    if (keycloakOrgId == null) return "member";
    try {
      return keycloakProvisioningClient.isOrgCreator(keycloakOrgId, keycloakUserId)
          ? "owner"
          : "member";
    } catch (Exception e) {
      log.warn("Could not determine org creator for {}: {}", keycloakUserId, e.getMessage());
      return "member";
    }
  }
}
