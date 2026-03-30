package io.github.rakheendama.starter.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "company", length = 255)
  private String company;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Customer() {}

  public Customer(String name, String email, String company, UUID createdBy) {
    this.name = name;
    this.email = email;
    this.company = company;
    this.status = "ACTIVE";
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void updateDetails(String name, String email, String company) {
    this.name = name;
    this.email = email;
    this.company = company;
    this.updatedAt = Instant.now();
  }

  public void archive() {
    this.status = "ARCHIVED";
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getCompany() {
    return company;
  }

  public String getStatus() {
    return status;
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
