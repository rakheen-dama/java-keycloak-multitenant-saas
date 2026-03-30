package io.github.rakheendama.starter.project;

import io.github.rakheendama.starter.exception.InvalidStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Project() {}

  public Project(String title, String description, UUID customerId, UUID createdBy) {
    this.title = title;
    this.description = description;
    this.customerId = customerId;
    this.createdBy = createdBy;
    this.status = "ACTIVE";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void updateDetails(String title, String description) {
    this.title = title;
    this.description = description;
    this.updatedAt = Instant.now();
  }

  public void changeStatus(String newStatus) {
    Set<String> validTargets =
        switch (this.status) {
          case "ACTIVE" -> Set.of("COMPLETED", "ARCHIVED");
          case "COMPLETED" -> Set.of("ARCHIVED", "ACTIVE");
          case "ARCHIVED" -> Set.of("ACTIVE");
          default -> Set.of();
        };
    if (!validTargets.contains(newStatus)) {
      throw new InvalidStateException(
          "Invalid project status transition",
          "Cannot transition from " + this.status + " to " + newStatus);
    }
    this.status = newStatus;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getStatus() {
    return status;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
