package io.github.rakheendama.starter.provisioning;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMappingRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.multitenancy.SchemaNameGenerator;
import io.github.rakheendama.starter.organization.OrganizationRepository;
import java.util.UUID;
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

  private static String uniqueSlug(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  @Test
  void provisionTenant_success_createsSchemaAndMapping() {
    String slug = uniqueSlug("acme");

    var result = tenantProvisioningService.provisionTenant(slug, "Acme Corp", "kc-" + slug);

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();

    var mapping = mappingRepository.findByOrgId(slug);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName()).isEqualTo(SchemaNameGenerator.generate(slug));
  }

  @Test
  void provisionTenant_idempotent_secondCallReturnsAlreadyProvisioned() {
    String slug = uniqueSlug("idem");

    tenantProvisioningService.provisionTenant(slug, "Idempotent Org", "kc-" + slug);

    var secondResult =
        tenantProvisioningService.provisionTenant(slug, "Idempotent Org", "kc-" + slug);

    assertThat(secondResult.alreadyProvisioned()).isTrue();
    assertThat(secondResult.success()).isTrue();

    var mappings = mappingRepository.findByOrgId(slug);
    assertThat(mappings).isPresent();
  }

  @Test
  void provisionTenant_createsOrganizationRecord() {
    String slug = uniqueSlug("orgrec");
    String kcOrgId = "kc-" + slug;
    String schemaName = SchemaNameGenerator.generate(slug);

    tenantProvisioningService.provisionTenant(slug, "Org Record Test", kcOrgId);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var org = organizationRepository.findByKeycloakOrgId(kcOrgId);
              assertThat(org).isPresent();
              assertThat(org.get().getStatus()).isEqualTo("COMPLETED");
              assertThat(org.get().getName()).isEqualTo("Org Record Test");
              assertThat(org.get().getSlug()).isEqualTo(slug);
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
    String slug = uniqueSlug("commit");

    tenantProvisioningService.provisionTenant(slug, "Commit Marker", "kc-" + slug);

    var mapping = mappingRepository.findByOrgId(slug);
    assertThat(mapping).isPresent();
    assertThat(mapping.get().getSchemaName()).isEqualTo(SchemaNameGenerator.generate(slug));
  }

  @Test
  void provisionTenant_runsTenantMigrations() {
    String slug = uniqueSlug("migtest");
    String schemaName = SchemaNameGenerator.generate(slug);

    tenantProvisioningService.provisionTenant(slug, "Migration Test", "kc-" + slug);

    // Verify the organizations table exists in the tenant schema
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + schemaName + ".organizations", Integer.class);
    assertThat(count).isNotNull().isGreaterThanOrEqualTo(1);
  }
}
