package io.github.rakheendama.starter.gateway.config;

import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** Extracts organization info from Keycloak OIDC claims. */
public final class BffUserInfoExtractor {

  public record OrgInfo(String slug, String id) {}

  private BffUserInfoExtractor() {}

  /**
   * Extracts organization info from the Keycloak {@code organization} claim.
   *
   * <p>Keycloak 26.x emits the claim in two possible formats:
   *
   * <ul>
   *   <li>List format (built-in org scope): {@code ["acme-corp"]} — alias only
   *   <li>Map format (rich mapper): {@code {"acme-corp": {"id": "uuid", "roles": [...]}}}
   * </ul>
   */
  @SuppressWarnings("unchecked")
  public static OrgInfo extractOrgInfo(OidcUser user) {
    if (user == null) {
      return null;
    }
    Object raw = user.getClaim("organization");
    if (raw == null) {
      return null;
    }

    // Keycloak 26.x built-in org scope emits a List<String> of org aliases
    if (raw instanceof List<?> list) {
      if (list.isEmpty()) return null;
      String alias = (String) list.getFirst();
      return new OrgInfo(alias, alias);
    }

    // Rich format: Map<alias, {id, roles}>
    if (raw instanceof Map<?, ?>) {
      Map<String, Object> orgClaim = (Map<String, Object>) raw;
      if (orgClaim.isEmpty()) return null;
      var entry = orgClaim.entrySet().iterator().next();
      String slug = entry.getKey();
      Map<String, Object> orgData = (Map<String, Object>) entry.getValue();
      String id = (String) orgData.getOrDefault("id", slug);
      return new OrgInfo(slug, id);
    }

    return null;
  }

  /** Convenience method to extract just the organization slug. */
  public static String extractOrgSlug(OidcUser user) {
    OrgInfo info = extractOrgInfo(user);
    return info != null ? info.slug() : null;
  }

  /** Convenience method to extract just the organization ID. */
  public static String extractOrgId(OidcUser user) {
    OrgInfo info = extractOrgInfo(user);
    return info != null ? info.id() : null;
  }
}
