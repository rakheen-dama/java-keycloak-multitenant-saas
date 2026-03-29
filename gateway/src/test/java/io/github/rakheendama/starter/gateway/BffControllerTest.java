package io.github.rakheendama.starter.gateway;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "spring.session.store-type=none",
      "spring.cloud.gateway.server.webmvc.routes[0].id=test-route",
      "spring.cloud.gateway.server.webmvc.routes[0].uri=http://localhost:8080",
      "spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/test/**",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
          + "org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration,"
          + "org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration,"
          + "org.springframework.cloud.autoconfigure.RefreshAutoConfiguration,"
          + "org.springframework.cloud.autoconfigure.RefreshEndpointAutoConfiguration,"
          + "org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration,"
          + "org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration,"
          + "org.springframework.cloud.client.serviceregistry.ServiceRegistryAutoConfiguration,"
          + "org.springframework.cloud.client.CommonsClientAutoConfiguration,"
          + "org.springframework.cloud.gateway.server.mvc.GatewayServerMvcAutoConfiguration,"
          + "org.springframework.cloud.gateway.server.mvc.filter.FilterAutoConfiguration,"
          + "org.springframework.cloud.gateway.server.mvc.GatewayMvcClassPathWarningAutoConfiguration",
      "spring.cloud.discovery.enabled=false",
      "spring.cloud.service-registry.auto-registration.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver"
    })
@Import(BffControllerTest.MockOAuth2Config.class)
class BffControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void me_unauthenticated_returnsUnauthenticatedResponse() throws Exception {
    mockMvc
        .perform(get("/bff/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(false))
        .andExpect(jsonPath("$.groups").isArray())
        .andExpect(jsonPath("$.groups").isEmpty());
  }

  @Test
  void csrf_returnsToken() throws Exception {
    mockMvc
        .perform(get("/bff/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.parameterName").isString())
        .andExpect(jsonPath("$.headerName").isString());
  }

  @Test
  void me_authenticated_returnsUserInfo() throws Exception {
    var oidcUser = buildOidcUser("user-uuid-123", "alice@example.com", "Alice Owner");

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authenticated").value(true))
        .andExpect(jsonPath("$.userId").value("user-uuid-123"))
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.name").value("Alice Owner"));
  }

  @Test
  void me_authenticated_returnsOrgInfo() throws Exception {
    var oidcUser = buildOidcUser("user-uuid-123", "alice@example.com", "Alice Owner");

    mockMvc
        .perform(get("/bff/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orgId").isNotEmpty())
        .andExpect(jsonPath("$.orgSlug").isNotEmpty())
        .andExpect(jsonPath("$.groups").isArray());
  }

  private DefaultOidcUser buildOidcUser(String subject, String email, String name) {
    OidcIdToken idToken =
        OidcIdToken.withTokenValue("mock-token")
            .subject(subject)
            .claim("email", email)
            .claim("name", name)
            .claim("picture", "https://example.com/photo.jpg")
            .claim(
                "organization",
                Map.of("acme-corp", Map.of("id", "org-uuid-456", "roles", List.of("owner"))))
            .issuer("https://keycloak.example.com/realms/starter")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
  }

  @TestConfiguration
  static class MockOAuth2Config {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("test")
              .clientSecret("test")
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri("https://example.com/auth")
              .tokenUri("https://example.com/token")
              .jwkSetUri("https://example.com/jwks")
              .userInfoUri("https://example.com/userinfo")
              .userNameAttributeName("sub")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
