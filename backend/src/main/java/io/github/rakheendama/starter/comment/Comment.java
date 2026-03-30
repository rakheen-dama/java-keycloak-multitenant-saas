package io.github.rakheendama.starter.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comments")
public class Comment {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "author_type", nullable = false, length = 20)
  private String authorType;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "author_name", length = 255)
  private String authorName;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Comment() {}

  /** Constructor for MEMBER comments (used in T5B). */
  public Comment(UUID projectId, String content, UUID memberId, String memberName) {
    this.projectId = projectId;
    this.content = content;
    this.authorType = "MEMBER";
    this.authorId = memberId;
    this.authorName = memberName;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Constructor for CUSTOMER comments (used in T6B). */
  public Comment(
      UUID projectId, String content, UUID customerId, String customerName, String authorType) {
    this.projectId = projectId;
    this.content = content;
    this.authorType = authorType;
    this.authorId = customerId;
    this.authorName = customerName;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getContent() {
    return content;
  }

  public String getAuthorType() {
    return authorType;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getAuthorName() {
    return authorName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
