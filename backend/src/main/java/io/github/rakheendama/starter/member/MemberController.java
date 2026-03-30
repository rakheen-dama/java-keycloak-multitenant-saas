package io.github.rakheendama.starter.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
public class MemberController {

  private final MemberService memberService;

  public MemberController(MemberService memberService) {
    this.memberService = memberService;
  }

  @GetMapping
  public ResponseEntity<List<MemberResponse>> listMembers() {
    return ResponseEntity.ok(
        memberService.listMembers().stream().map(MemberResponse::from).toList());
  }

  @GetMapping("/me")
  public ResponseEntity<MemberResponse> getMe() {
    return ResponseEntity.ok(MemberResponse.from(memberService.getCurrentMember()));
  }

  @PostMapping("/invite")
  public ResponseEntity<Void> inviteMember(@Valid @RequestBody InviteMemberRequest request) {
    memberService.inviteMember(request.email());
    return ResponseEntity.status(201).build();
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> removeMember(@PathVariable UUID id) {
    memberService.removeMember(id);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/{id}/role")
  public ResponseEntity<MemberResponse> changeRole(
      @PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
    return ResponseEntity.ok(MemberResponse.from(memberService.changeRole(id, request.role())));
  }

  // --- DTOs ---

  record InviteMemberRequest(@NotBlank @Email String email) {}

  record ChangeRoleRequest(@NotBlank String role) {}

  record MemberResponse(
      UUID id,
      String email,
      String displayName,
      String role,
      String status,
      String firstLoginAt,
      String lastLoginAt,
      String createdAt) {

    static MemberResponse from(Member m) {
      return new MemberResponse(
          m.getId(),
          m.getEmail(),
          m.getDisplayName(),
          m.getRole(),
          m.getStatus(),
          m.getFirstLoginAt() != null ? m.getFirstLoginAt().toString() : null,
          m.getLastLoginAt() != null ? m.getLastLoginAt().toString() : null,
          m.getCreatedAt().toString());
    }
  }
}
