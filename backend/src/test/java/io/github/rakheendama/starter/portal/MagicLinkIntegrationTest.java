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
    // Clean and seed inside tenant schema
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .where(RequestScopes.ORG_ID, orgSlug)
        .run(
            () -> {
              magicLinkTokenRepository.deleteAll();
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
    String requestBody =
        """
        { "email": "%s", "orgId": "%s" }
        """
            .formatted(CUSTOMER_EMAIL, orgSlug);
    for (int i = 0; i < 3; i++) {
      mockMvc
          .perform(
              post("/api/portal/auth/request-link")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk());
    }

    mockMvc
        .perform(
            post("/api/portal/auth/request-link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void exchange_validToken_returnsPortalJwt() throws Exception {
    String rawToken =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .call(() -> magicLinkService.generateToken(customerId, orgSlug, "127.0.0.1"));

    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "token": "%s", "orgId": "%s" }
                    """
                        .formatted(rawToken, orgSlug)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.customerId").value(customerId.toString()))
        .andExpect(jsonPath("$.customerName").value(CUSTOMER_NAME));
  }

  @Test
  void exchange_expiredToken_returns401() throws Exception {
    String rawToken = "expired-test-token-" + UUID.randomUUID();
    String tokenHash = MagicLinkService.hashToken(rawToken);
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () -> {
              var expiredToken =
                  new MagicLinkToken(
                      customerId, tokenHash, Instant.now().minus(1, ChronoUnit.HOURS), "127.0.0.1");
              magicLinkTokenRepository.save(expiredToken);
            });

    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "token": "%s", "orgId": "%s" }
                    """
                        .formatted(rawToken, orgSlug)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void exchange_usedToken_returns401() throws Exception {
    String rawToken =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .call(() -> magicLinkService.generateToken(customerId, orgSlug, "127.0.0.1"));

    String exchangeBody =
        """
        { "token": "%s", "orgId": "%s" }
        """
            .formatted(rawToken, orgSlug);

    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exchangeBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/portal/auth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(exchangeBody))
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
                    { "token": "totally-invalid-garbage-token", "orgId": "%s" }
                    """
                        .formatted(orgSlug)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void portalJwt_containsCorrectClaims() throws Exception {
    String rawToken =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
            .call(() -> magicLinkService.generateToken(customerId, orgSlug, "127.0.0.1"));

    var result =
        mockMvc
            .perform(
                post("/api/portal/auth/exchange")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "token": "%s", "orgId": "%s" }
                        """
                            .formatted(rawToken, orgSlug)))
            .andExpect(status().isOk())
            .andReturn();

    String jwtToken =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");

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
    String portalJwt = portalJwtService.issueToken(customerId, orgSlug);

    var result =
        mockMvc
            .perform(get("/api/portal/projects").header("Authorization", "Bearer " + portalJwt))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isNotEqualTo(401);
  }
}
