package io.github.rakheendama.starter.security;

import io.github.rakheendama.starter.exception.ForbiddenException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class PlatformSecurityService {

  /** Returns true if the JWT contains the "platform-admins" group. */
  public boolean isPlatformAdmin(Jwt jwt) {
    return JwtUtils.extractGroups(jwt).contains("platform-admins");
  }

  /** Throws ForbiddenException if the JWT does not contain the "platform-admins" group. */
  public void requirePlatformAdmin(Jwt jwt) {
    if (!isPlatformAdmin(jwt)) {
      throw new ForbiddenException(
          "Platform admin required", "This action requires platform admin privileges");
    }
  }
}
