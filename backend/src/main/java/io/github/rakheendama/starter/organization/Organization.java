package io.github.rakheendama.starter.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "keycloak_org_id", nullable = false, unique = true)
  private String keycloakOrgId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false, length = 100)
  private String slug;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Organization() {}

  public Organization(String keycloakOrgId, String name, String slug) {
    this.keycloakOrgId = keycloakOrgId;
    this.name = name;
    this.slug = slug;
    this.status = "IN_PROGRESS";
  }

  @PrePersist
  protected void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public void markCompleted() {
    this.status = "COMPLETED";
    this.updatedAt = Instant.now();
  }

  public void markFailed() {
    this.status = "FAILED";
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getKeycloakOrgId() {
    return keycloakOrgId;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
