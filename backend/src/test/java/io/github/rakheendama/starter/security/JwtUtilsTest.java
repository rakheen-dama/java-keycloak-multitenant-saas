package io.github.rakheendama.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtUtilsTest {

  private static Jwt.Builder baseJwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .subject("user-1");
  }

  @Test
  void extractOrgId_listFormat_returnsFirstAlias() {
    Jwt jwt = baseJwt().claim("organization", List.of("my-org-slug")).build();
    assertThat(JwtUtils.extractOrgId(jwt)).isEqualTo("my-org-slug");
  }

  @Test
  void extractOrgId_richMapFormat_returnsAlias() {
    Jwt jwt =
        baseJwt()
            .claim(
                "organization",
                Map.of("my-org-slug", Map.of("id", "uuid-123", "roles", List.of("owner"))))
            .build();
    assertThat(JwtUtils.extractOrgId(jwt)).isEqualTo("my-org-slug");
  }

  @Test
  void extractOrgId_noClaim_returnsNull() {
    Jwt jwt = baseJwt().build();
    assertThat(JwtUtils.extractOrgId(jwt)).isNull();
  }

  @Test
  void extractSub_returnsSubject() {
    Jwt jwt = baseJwt().build();
    assertThat(JwtUtils.extractSub(jwt)).isEqualTo("user-1");
  }

  @Test
  void extractEmail_present_returnsEmail() {
    Jwt jwt = baseJwt().claim("email", "alice@example.com").build();
    assertThat(JwtUtils.extractEmail(jwt)).isEqualTo("alice@example.com");
  }

  @Test
  void extractEmail_absent_returnsNull() {
    assertThat(JwtUtils.extractEmail(baseJwt().build())).isNull();
  }

  @Test
  void extractName_present_returnsName() {
    Jwt jwt = baseJwt().claim("name", "Alice Smith").build();
    assertThat(JwtUtils.extractName(jwt)).isEqualTo("Alice Smith");
  }

  @Test
  void extractGroups_present_returnsSet() {
    Jwt jwt = baseJwt().claim("groups", List.of("platform-admins", "other")).build();
    assertThat(JwtUtils.extractGroups(jwt)).containsExactlyInAnyOrder("platform-admins", "other");
  }

  @Test
  void extractGroups_absent_returnsEmpty() {
    assertThat(JwtUtils.extractGroups(baseJwt().build())).isEmpty();
  }
}
