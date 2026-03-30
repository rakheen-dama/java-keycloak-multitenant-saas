package io.github.rakheendama.starter.member;

import io.github.rakheendama.starter.exception.InvalidStateException;
import io.github.rakheendama.starter.exception.ResourceNotFoundException;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.organization.OrganizationRepository;
import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

  private static final Set<String> VALID_ROLES = Set.of("owner", "member");

  private final MemberRepository memberRepository;
  private final OrganizationRepository organizationRepository;
  private final KeycloakProvisioningClient keycloakProvisioningClient;

  public MemberService(
      MemberRepository memberRepository,
      OrganizationRepository organizationRepository,
      KeycloakProvisioningClient keycloakProvisioningClient) {
    this.memberRepository = memberRepository;
    this.organizationRepository = organizationRepository;
    this.keycloakProvisioningClient = keycloakProvisioningClient;
  }

  public List<Member> listMembers() {
    return memberRepository.findAll();
  }

  public Member getCurrentMember() {
    return memberRepository
        .findById(RequestScopes.requireMemberId())
        .orElseThrow(
            () ->
                ResourceNotFoundException.withDetail(
                    "Member not found", "Current member record missing"));
  }

  public void inviteMember(String email) {
    RequestScopes.requireOwner();
    var org =
        organizationRepository.findAll().stream()
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("Organization record not found in tenant schema"));
    keycloakProvisioningClient.inviteUser(org.getKeycloakOrgId(), email);
  }

  @Transactional
  public void removeMember(UUID memberId) {
    RequestScopes.requireOwner();
    UUID callerId = RequestScopes.requireMemberId();
    if (memberId.equals(callerId)) {
      throw new InvalidStateException("Cannot remove self", "An owner cannot remove themselves");
    }
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    memberRepository.delete(member);
  }

  @Transactional
  public Member changeRole(UUID memberId, String newRole) {
    RequestScopes.requireOwner();
    if (!VALID_ROLES.contains(newRole)) {
      throw new InvalidStateException(
          "Invalid role", "Role must be 'owner' or 'member', got: " + newRole);
    }
    var member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    member.changeRole(newRole);
    return memberRepository.save(member);
  }
}
