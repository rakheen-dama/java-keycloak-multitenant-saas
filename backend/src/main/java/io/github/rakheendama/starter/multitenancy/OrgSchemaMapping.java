package io.github.rakheendama.starter.multitenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "org_schema_mappings", schema = "public")
public class OrgSchemaMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false, unique = true)
  private String orgId;

  @Column(name = "schema_name", nullable = false, unique = true)
  private String schemaName;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected OrgSchemaMapping() {}

  public OrgSchemaMapping(String orgId, String schemaName) {
    this.orgId = orgId;
    this.schemaName = schemaName;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
