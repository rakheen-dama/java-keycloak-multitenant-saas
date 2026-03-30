package io.github.rakheendama.starter.provisioning;

import io.github.rakheendama.starter.multitenancy.OrgSchemaMapping;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMappingRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.multitenancy.SchemaNameGenerator;
import io.github.rakheendama.starter.organization.Organization;
import io.github.rakheendama.starter.organization.OrganizationRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TenantProvisioningService {

  private static final Logger log = LoggerFactory.getLogger(TenantProvisioningService.class);
  private static final String SCHEMA_NAME_PATTERN = "^tenant_[0-9a-f]{12}$";

  private final OrgSchemaMappingRepository mappingRepository;
  private final OrganizationRepository organizationRepository;
  private final DataSource dataSource;
  private final TransactionTemplate txTemplate;

  public TenantProvisioningService(
      OrgSchemaMappingRepository mappingRepository,
      OrganizationRepository organizationRepository,
      DataSource dataSource,
      TransactionTemplate txTemplate) {
    this.mappingRepository = mappingRepository;
    this.organizationRepository = organizationRepository;
    this.dataSource = dataSource;
    this.txTemplate = txTemplate;
  }

  public ProvisioningResult provisionTenant(String orgSlug, String orgName, String keycloakOrgId) {
    // Step 1: Idempotency check — if mapping exists, tenant is fully provisioned
    var existingMapping = mappingRepository.findByOrgId(orgSlug);
    if (existingMapping.isPresent()) {
      log.info(
          "Tenant already provisioned for org '{}' with schema '{}'",
          orgSlug,
          existingMapping.get().getSchemaName());
      return ProvisioningResult.alreadyProvisioned(existingMapping.get().getSchemaName());
    }

    // Step 3: Generate deterministic schema name
    String schemaName = SchemaNameGenerator.generate(orgSlug);
    validateSchemaName(schemaName);

    Organization org = null;
    try {
      // Step 4: Create schema
      log.info("Creating schema '{}'", schemaName);
      new JdbcTemplate(dataSource).execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");

      // Step 5: Run Flyway tenant migrations
      log.info("Running tenant migrations for schema '{}'", schemaName);
      Flyway.configure()
          .dataSource(dataSource)
          .locations("classpath:db/migration/tenant")
          .schemas(schemaName)
          .baselineOnMigrate(true)
          .load()
          .migrate();

      // Step 2: Find or create Organization record (after schema + tables exist)
      org = findOrCreateOrganization(schemaName, keycloakOrgId, orgName, orgSlug);

      // Step 6: Create OrgSchemaMapping (commit marker) — skip if already present
      if (mappingRepository.findByOrgId(orgSlug).isEmpty()) {
        txTemplate.executeWithoutResult(
            tx -> mappingRepository.save(new OrgSchemaMapping(orgSlug, schemaName)));
        log.info("Created OrgSchemaMapping: {} -> {}", orgSlug, schemaName);
      }

      // Step 7: Mark organization completed
      markOrganizationCompleted(schemaName, org);

      log.info("Tenant provisioning completed for org '{}' (schema={})", orgSlug, schemaName);
      return ProvisioningResult.success(schemaName);
    } catch (Exception e) {
      log.error("Provisioning failed for org '{}': {}", orgSlug, e.getMessage(), e);
      if (org != null) {
        markOrganizationFailed(schemaName, org, e.getMessage());
      }
      throw new ProvisioningException(
          "Failed to provision tenant for org '" + orgSlug + "': " + e.getMessage(), e);
    }
  }

  private Organization findOrCreateOrganization(
      String schemaName, String keycloakOrgId, String orgName, String orgSlug) {
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
          .call(
              () ->
                  txTemplate.execute(
                      tx ->
                          organizationRepository
                              .findByKeycloakOrgId(keycloakOrgId)
                              .orElseGet(
                                  () -> {
                                    var newOrg = new Organization(keycloakOrgId, orgName, orgSlug);
                                    return organizationRepository.save(newOrg);
                                  })));
    } catch (Exception e) {
      if (e instanceof RuntimeException re) {
        throw re;
      }
      throw new RuntimeException(e);
    }
  }

  private void markOrganizationCompleted(String schemaName, Organization org) {
    try {
      ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
          .run(
              () ->
                  txTemplate.executeWithoutResult(
                      tx -> {
                        org.markCompleted();
                        organizationRepository.save(org);
                      }));
    } catch (Exception e) {
      log.warn("Failed to mark organization completed: {}", e.getMessage());
    }
  }

  private void markOrganizationFailed(String schemaName, Organization org, String errorMessage) {
    try {
      ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
          .run(
              () ->
                  txTemplate.executeWithoutResult(
                      tx -> {
                        org.markFailed();
                        organizationRepository.save(org);
                      }));
    } catch (Exception e) {
      log.warn("Failed to mark organization failed: {}", e.getMessage());
    }
  }

  private void validateSchemaName(String schemaName) {
    if (!schemaName.matches(SCHEMA_NAME_PATTERN)) {
      throw new IllegalArgumentException(
          "Invalid schema name: " + schemaName + " (must match " + SCHEMA_NAME_PATTERN + ")");
    }
  }
}
