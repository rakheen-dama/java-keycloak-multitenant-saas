package io.github.rakheendama.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rakheendama.starter.exception.ForbiddenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PlatformSecurityServiceTest {

  private final PlatformSecurityService service = new PlatformSecurityService();

  private static Jwt.Builder baseJwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .subject("user-1");
  }

  @Test
  void isPlatformAdmin_withGroup_returnsTrue() {
    Jwt jwt = baseJwt().claim("groups", List.of("platform-admins")).build();
    assertThat(service.isPlatformAdmin(jwt)).isTrue();
  }

  @Test
  void isPlatformAdmin_withoutGroup_returnsFalse() {
    Jwt jwt = baseJwt().claim("groups", List.of("other-group")).build();
    assertThat(service.isPlatformAdmin(jwt)).isFalse();
  }

  @Test
  void isPlatformAdmin_noGroupsClaim_returnsFalse() {
    Jwt jwt = baseJwt().build();
    assertThat(service.isPlatformAdmin(jwt)).isFalse();
  }

  @Test
  void requirePlatformAdmin_nonAdmin_throwsForbidden() {
    Jwt jwt = baseJwt().build();
    assertThatThrownBy(() -> service.requirePlatformAdmin(jwt))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void requirePlatformAdmin_platformAdmin_doesNotThrow() {
    Jwt jwt = baseJwt().claim("groups", List.of("platform-admins")).build();
    // Should not throw
    service.requirePlatformAdmin(jwt);
  }
}
