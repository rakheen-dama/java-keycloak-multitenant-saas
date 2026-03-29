package io.github.rakheendama.starter.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts claims from Keycloak JWTs.
 *
 * <p>Keycloak org claim formats:
 *
 * <ul>
 *   <li>List format: {@code "organization": ["my-org-slug"]}
 *   <li>Rich map format: {@code "organization": { "my-org-slug": { "id": "uuid", "roles": [...] }
 *       }}
 * </ul>
 */
public final class JwtUtils {

  private static final String ORG_CLAIM = "organization";
  private static final String GROUPS_CLAIM = "groups";

  /**
   * Extracts the org alias from the Keycloak "organization" claim. Returns the first alias from
   * list or map format, or null if absent.
   */
  @SuppressWarnings("unchecked")
  public static String extractOrgId(Jwt jwt) {
    Object claim = jwt.getClaim(ORG_CLAIM);
    if (claim instanceof List<?> list && !list.isEmpty()) {
      return (String) list.getFirst();
    }
    if (claim instanceof Map<?, ?> map && !map.isEmpty()) {
      return (String) map.keySet().iterator().next();
    }
    return null;
  }

  /** Returns the JWT subject claim ("sub"). */
  public static String extractSub(Jwt jwt) {
    return jwt.getSubject();
  }

  /** Returns the "email" claim, or null if absent. */
  public static String extractEmail(Jwt jwt) {
    return jwt.getClaimAsString("email");
  }

  /** Returns the "name" claim, or null if absent. */
  public static String extractName(Jwt jwt) {
    return jwt.getClaimAsString("name");
  }

  /** Extracts the "groups" claim as an unmodifiable Set. Returns empty set if absent. */
  @SuppressWarnings("unchecked")
  public static Set<String> extractGroups(Jwt jwt) {
    Object claim = jwt.getClaim(GROUPS_CLAIM);
    if (claim instanceof List<?> list && !list.isEmpty()) {
      return Collections.unmodifiableSet(new LinkedHashSet<>((List<String>) list));
    }
    return Collections.emptySet();
  }

  private JwtUtils() {}
}
