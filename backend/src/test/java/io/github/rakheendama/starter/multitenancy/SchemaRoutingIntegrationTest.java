package io.github.rakheendama.starter.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SchemaRoutingIntegrationTest {

  @Autowired DataSource dataSource;

  @Autowired OrgSchemaMappingRepository orgSchemaMappingRepository;

  @Autowired SchemaMultiTenantConnectionProvider connectionProvider;

  @Test
  void publicMigrationsRanOnStartup() throws SQLException {
    List<String> tables = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' "
                        + "AND table_name IN ('access_requests', 'org_schema_mappings') "
                        + "ORDER BY table_name")) {
      while (rs.next()) {
        tables.add(rs.getString("table_name"));
      }
    }
    assertThat(tables).containsExactly("access_requests", "org_schema_mappings");
  }

  @Test
  void tenantMigrationsCreateTablesInNewSchema() throws SQLException {
    String schemaName = "tenant_test000001";

    try (Connection conn = dataSource.getConnection()) {
      conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
    }

    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/tenant")
        .schemas(schemaName)
        .baselineOnMigrate(true)
        .load()
        .migrate();

    List<String> tables = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        ResultSet rs =
            conn.createStatement()
                .executeQuery(
                    "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = '"
                        + schemaName
                        + "' "
                        + "AND table_name IN ('organizations', 'members') "
                        + "ORDER BY table_name")) {
      while (rs.next()) {
        tables.add(rs.getString("table_name"));
      }
    }
    assertThat(tables).containsExactly("members", "organizations");
  }

  @Test
  void orgSchemaMappingPersistsAndRetrieves() {
    OrgSchemaMapping mapping = new OrgSchemaMapping("test-org-crud", "tenant_abc123def456");
    orgSchemaMappingRepository.save(mapping);

    var found = orgSchemaMappingRepository.findByOrgId("test-org-crud");
    assertThat(found).isPresent();
    assertThat(found.get().getSchemaName()).isEqualTo("tenant_abc123def456");
    assertThat(found.get().getOrgId()).isEqualTo("test-org-crud");
    assertThat(found.get().getId()).isNotNull();
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  void schemaNameGeneratorProducesDeterministicNames() {
    String name1 = SchemaNameGenerator.generate("acme-corp");
    String name2 = SchemaNameGenerator.generate("acme-corp");
    assertThat(name1).isEqualTo(name2);
    assertThat(name1).matches("^tenant_[0-9a-f]{12}$");

    String different = SchemaNameGenerator.generate("other-org");
    assertThat(different).isNotEqualTo(name1);
  }

  @Test
  void connectionProviderSetsSearchPath() throws SQLException {
    // Create the schema first so SET search_path doesn't fail on Postgres
    String schemaName = "tenant_a1b2c3d4e5f6";
    try (Connection setup = dataSource.getConnection()) {
      setup.createStatement().execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
    }

    Connection conn = connectionProvider.getConnection(schemaName);
    try {
      String searchPath;
      try (ResultSet rs = conn.createStatement().executeQuery("SHOW search_path")) {
        rs.next();
        searchPath = rs.getString(1);
      }
      assertThat(searchPath).isEqualTo(schemaName);
    } finally {
      connectionProvider.releaseConnection(schemaName, conn);
    }

    // After release, get a new connection and verify it's reset to public
    try (Connection fresh = dataSource.getConnection();
        ResultSet rs = fresh.createStatement().executeQuery("SHOW search_path")) {
      rs.next();
      String resetPath = rs.getString(1);
      // Fresh connections from the pool may have "public" or default search_path
      assertThat(resetPath).contains("public");
    }
  }
}
