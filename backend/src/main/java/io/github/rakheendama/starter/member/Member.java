package io.github.rakheendama.starter.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "members")
public class Member {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "keycloak_user_id", nullable = false, unique = true)
  private String keycloakUserId;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "display_name")
  private String displayName;

  @Column(name = "role", nullable = false)
  private String role;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "first_login_at")
  private Instant firstLoginAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Member() {}

  public Member(String keycloakUserId, String email, String displayName, String role) {
    this.keycloakUserId = keycloakUserId;
    this.email = email;
    this.displayName = displayName;
    this.role = role;
    this.status = "ACTIVE";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getKeycloakUserId() {
    return keycloakUserId;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getRole() {
    return role;
  }

  public String getStatus() {
    return status;
  }

  public Instant getFirstLoginAt() {
    return firstLoginAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
