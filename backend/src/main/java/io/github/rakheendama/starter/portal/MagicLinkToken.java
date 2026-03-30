package io.github.rakheendama.starter.portal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "token_hash", nullable = false, length = 255)
  private String tokenHash;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_ip", length = 45)
  private String createdIp;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MagicLinkToken() {}

  public MagicLinkToken(UUID customerId, String tokenHash, Instant expiresAt, String createdIp) {
    this.customerId = customerId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdIp = createdIp;
    this.createdAt = Instant.now();
  }

  public void markUsed() {
    this.usedAt = Instant.now();
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public String getCreatedIp() {
    return createdIp;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
