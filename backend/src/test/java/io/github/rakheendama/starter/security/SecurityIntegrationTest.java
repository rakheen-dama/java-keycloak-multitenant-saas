package io.github.rakheendama.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMapping;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMappingRepository;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private DataSource dataSource;

  @Test
  void unauthenticatedRequest_toApi_returns401() throws Exception {
    mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
  }

  @Test
  void authenticatedRequest_withProvisionedOrg_resolvesTenant() throws Exception {
    // Seed the org-schema mapping and create the tenant schema
    // Schema must match pattern: tenant_ + 12 hex chars
    String orgAlias = "test-org-provisioned";
    String schemaName = "tenant_abcdef012345";

    try (Connection conn = dataSource.getConnection()) {
      var stmt = conn.createStatement();
      stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
      // Run tenant migrations so MemberFilter can query the members table
      stmt.execute("SET search_path TO " + schemaName);
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS organizations (
              id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
              keycloak_org_id VARCHAR(255) NOT NULL UNIQUE,
              name            VARCHAR(255) NOT NULL,
              slug            VARCHAR(100) NOT NULL,
              status          VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
              created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
              updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
          )
          """);
      stmt.execute(
          """
          CREATE TABLE IF NOT EXISTS members (
              id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
              keycloak_user_id VARCHAR(255) NOT NULL UNIQUE,
              email           VARCHAR(255) NOT NULL,
              display_name    VARCHAR(255),
              role            VARCHAR(20) NOT NULL,
              status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
              first_login_at  TIMESTAMPTZ,
              last_login_at   TIMESTAMPTZ,
              created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
              updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
          )
          """);
      stmt.execute("SET search_path TO public");
    }
    orgSchemaMappingRepository.save(new OrgSchemaMapping(orgAlias, schemaName));

    // Authenticated request with a JWT carrying the provisioned org
    // Should pass auth (not 401) and pass TenantFilter (not 403)
    // Returns 404 because no /api/projects controller exists
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_123")
                                    .claim("organization", List.of(orgAlias)))))
        .andExpect(status().isNotFound());
  }

  @Test
  void authenticatedRequest_withUnprovisionedOrg_returns403() throws Exception {
    mockMvc
        .perform(
            get("/api/projects")
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("user_123")
                                    .claim(
                                        "organization",
                                        Map.of(
                                            "nonexistent-org",
                                            Map.of("id", "uuid-999", "roles", List.of("member")))))))
        .andExpect(status().isForbidden());
  }

  @Test
  void accessRequests_isPublic() throws Exception {
    // POST /api/access-requests is permitAll() — should not return 401
    // May return 405 (no GET handler) or 400 (missing body), but NOT 401
    int statusCode =
        mockMvc.perform(post("/api/access-requests")).andReturn().getResponse().getStatus();
    assertThat(statusCode).isNotEqualTo(401);
  }

  @Test
  void actuatorHealth_isPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void jwtUtils_extractsClaimsFromBothFormats() {
    // List format
    Jwt listJwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .subject("user-1")
            .claim("organization", List.of("org-slug-list"))
            .build();
    assertThat(JwtUtils.extractOrgId(listJwt)).isEqualTo("org-slug-list");

    // Map format
    Jwt mapJwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .subject("user-2")
            .claim(
                "organization",
                Map.of("org-slug-map", Map.of("id", "uuid-456", "roles", List.of("owner"))))
            .build();
    assertThat(JwtUtils.extractOrgId(mapJwt)).isEqualTo("org-slug-map");
  }
}
