package io.github.rakheendama.starter.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jwt.SignedJWT;
import io.github.rakheendama.starter.TestcontainersConfiguration;
import io.github.rakheendama.starter.customer.Customer;
import io.github.rakheendama.starter.customer.CustomerRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.multitenancy.SchemaNameGenerator;
import io.github.rakheendama.starter.provisioning.KeycloakProvisioningClient;
import io.github.rakheendama.starter.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MagicLinkIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private MagicLinkTokenRepository magicLinkTokenRepository;
  @Autowired private MagicLinkService magicLinkService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private TenantProvisioningService tenantProvisioningService;

  @MockitoBean private KeycloakProvisioningClient keycloakProvisioningClient;
  @MockitoBean private JavaMailSender javaMailSender;

  private String orgSlug;
  private String schemaName;
  private UUID customerId;
  private static final String CUSTOMER_EMAIL = "portal-customer@test.com";
  private static final String CUSTOMER_NAME = "Portal Test Customer";

  @BeforeAll
  void provisionTenant() {
    orgSlug = "test-org-" + UUID.randomUUID().toString().substring(0, 8);
    schemaName = SchemaNameGenerator.generate(orgSlug);
    tenantProvisioningService.provisionTenant(orgSlug, "Test Org", "kc-" + orgSlug);
  }

  @BeforeEach
  void setUp() {
    // Clean magic link tokens (public schema — no scoped value needed)
    magicLinkTokenRepository.deleteAll();

    // Seed customer in tenant schema
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              customerRepository.deleteAll();
              var customer = new Customer(CUSTOMER_NAME, CUSTOMER_EMAIL, "Test Co", null);
              customer = customerRepository.save(customer);
              customerId = customer.getId();
            });
  }

  @Test
  void requestLink_validEmail_returns200WithGenericMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/portal/auth/request-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "%s", "orgId": "%s" }
                    """
                        .formatted(CUSTOMER_EMAIL, orgSlug)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."));
  }

  @Test
  void requestLink_unknownEmail_returnsSameGenericMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/portal/auth/request-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "nobody@unknown.com", "orgId": "%s" }
                    """
                        .formatted(orgSlug)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."));
  }

  @Test
  void requestLink_unknownOrg_returnsSameGenericMessage() throws Exception {
    mockMvc
        .perform(
            post("/api/portal/auth/request-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "%s", "orgId": "nonexistent-org" }
                    """
                        .formatted(CUSTOMER_EMAIL)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("If an account exists, a link has been sent."));
  }

  @Test
  void requestLink_rateLimitExceeded_returns429() throws Exception {
    // Generate 3 tokens directly via service (within scoped value for customer lookup)
    for (int i = 0; i < 3; i++) {
      magicLinkService.generateToken(customerId, orgSlug, "127.0.0.1");
    }

    // 4th request should hit rate limit
    mockMvc
        .perform(
            post("/api/portal/auth/request-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "email": "%s", "orgId": "%s" }
                    """
                        .formatted(CUSTOMER_EMAIL, orgSlug)))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void exchange_validToken_returnsPortalJwt() throws Exception {
    // Generate a token via the service
    String rawToken = magicLinkService.generateToken(customerId, orgSlug, "127.0.0.1");

    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "token": "%s" }
                    """
                        .formatted(rawToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.customerName").value(CUSTOMER_NAME));
  }

  @Test
  void exchange_expiredToken_returns401() throws Exception {
    // Insert an expired token directly into the public schema
    String rawToken = "expired-test-token-" + UUID.randomUUID();
    String tokenHash = MagicLinkService.hashToken(rawToken);
    var expiredToken =
        new MagicLinkToken(
            customerId, orgSlug, tokenHash, Instant.now().minus(1, ChronoUnit.HOURS), "127.0.0.1");
    magicLinkTokenRepository.save(expiredToken);

    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "token": "%s" }
                    """
                        .formatted(rawToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void exchange_usedToken_returns401() throws Exception {
    String rawToken = magicLinkService.generateToken(customerId, orgSlug, "127.0.0.1");

    // First exchange succeeds
    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "token": "%s" }
                    """
                        .formatted(rawToken)))
        .andExpect(status().isOk());

    // Second exchange fails — token already used
    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "token": "%s" }
                    """
                        .formatted(rawToken)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void exchange_invalidToken_returns401() throws Exception {
    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "token": "totally-invalid-garbage-token" }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void portalJwt_containsCorrectClaims() throws Exception {
    String rawToken = magicLinkService.generateToken(customerId, orgSlug, "127.0.0.1");

    var result =
        mockMvc
            .perform(
                post("/api/portal/auth/exchange")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "token": "%s" }
                        """
                            .formatted(rawToken)))
            .andExpect(status().isOk())
            .andReturn();

    String jwtToken =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");

    // Parse and verify JWT claims
    var signedJwt = SignedJWT.parse(jwtToken);
    var claims = signedJwt.getJWTClaimsSet();

    assertThat(claims.getSubject()).isEqualTo(customerId.toString());
    assertThat(claims.getStringClaim("org_id")).isEqualTo(orgSlug);
    assertThat(claims.getStringClaim("type")).isEqualTo("customer");
    assertThat(claims.getExpirationTime()).isNotNull();
    assertThat(claims.getJWTID()).isNotNull();
  }

  @Test
  void portalAuthFilter_validJwt_allowsPortalRequest() throws Exception {
    // Issue a portal JWT directly
    String portalJwt = portalJwtService.issueToken(customerId, orgSlug);

    // GET /api/portal/projects — no controller yet, but filter should NOT return 401
    // It should return 404 (no mapping) instead of 401 (auth failure)
    var result =
        mockMvc
            .perform(
                get("/api/portal/projects")
                    .header("Authorization", "Bearer " + portalJwt))
            .andReturn();

    // The filter should pass — the response should NOT be 401
    assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
  }
}
