package io.github.rakheendama.starter.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMappingRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.multitenancy.SchemaNameGenerator;
import io.github.rakheendama.starter.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantProvisioningIntegrationTest {

  @Autowired private TenantProvisioningService tenantProvisioningService;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private JavaMailSender javaMailSender;

  @Test
  void provisionTenant_success_createsSchemaAndMapping() {
    var result = tenantProvisioningService.provisionTenant("acme-corp", "Acme Corp", "kc-org-123");

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();

    var mapping = mappingRepository.findByOrgId("acme-corp");
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName())
        .isEqualTo(SchemaNameGenerator.generate("acme-corp"));
  }

  @Test
  void provisionTenant_idempotent_secondCallReturnsAlreadyProvisioned() {
    tenantProvisioningService.provisionTenant("idempotent-org", "Idempotent Org", "kc-idem-001");

    var secondResult =
        tenantProvisioningService.provisionTenant(
            "idempotent-org", "Idempotent Org", "kc-idem-001");

    assertThat(secondResult.alreadyProvisioned()).isTrue();
    assertThat(secondResult.success()).isTrue();

    var mappings = mappingRepository.findByOrgId("idempotent-org");
    assertThat(mappings).isPresent();
  }

  @Test
  void provisionTenant_createsOrganizationRecord() {
    String schemaName = SchemaNameGenerator.generate("org-record-test");
    tenantProvisioningService.provisionTenant(
        "org-record-test", "Org Record Test", "kc-org-record");

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var org = organizationRepository.findByKeycloakOrgId("kc-org-record");
              assertThat(org).isPresent();
              assertThat(org.get().getStatus()).isEqualTo("COMPLETED");
              assertThat(org.get().getName()).isEqualTo("Org Record Test");
              assertThat(org.get().getSlug()).isEqualTo("org-record-test");
            });
  }

  @Test
  void provisionTenant_schemaNameIsDeterministic() {
    String schemaName = SchemaNameGenerator.generate("acme-corp");
    assertThat(schemaName).startsWith("tenant_");
    assertThat(schemaName).hasSize(19); // "tenant_" (7) + 12 hex chars
    // Verify determinism
    assertThat(SchemaNameGenerator.generate("acme-corp")).isEqualTo(schemaName);
  }

  @Test
  void provisionTenant_orgSchemaMappingIsCommitMarker() {
    tenantProvisioningService.provisionTenant("commit-marker", "Commit Marker", "kc-commit-001");

    var mapping = mappingRepository.findByOrgId("commit-marker");
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName())
        .isEqualTo(SchemaNameGenerator.generate("commit-marker"));
  }

  @Test
  void provisionTenant_runsTenantMigrations() {
    String schemaName = SchemaNameGenerator.generate("migration-test");
    tenantProvisioningService.provisionTenant(
        "migration-test", "Migration Test", "kc-migration-001");

    // Verify the organizations table exists in the tenant schema
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + schemaName + ".organizations", Integer.class);
    assertThat(count).isNotNull().isGreaterThanOrEqualTo(1);
  }
}
