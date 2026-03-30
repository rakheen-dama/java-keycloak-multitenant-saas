package io.github.rakheendama.starter.multitenancy;

import io.github.rakheendama.starter.exception.ForbiddenException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Request-scoped values for multitenancy and member identity. Bound by servlet filters, read by
 * controllers/services/Hibernate.
 *
 * <p>Uses Java 25 ScopedValues (JEP 506) — immutable within their scope and automatically unbound
 * when the binding lambda exits. Virtual-thread-safe: zero per-thread overhead.
 */
public final class RequestScopes {

  /** Tenant schema name (e.g., "tenant_a1b2c3d4e5f6"). Bound by TenantFilter. */
  public static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();

  /** Keycloak org alias (from JWT "organization" claim). Bound by TenantFilter. */
  public static final ScopedValue<String> ORG_ID = ScopedValue.newInstance();

  /** Current member's UUID within the tenant. Bound by MemberFilter. */
  public static final ScopedValue<UUID> MEMBER_ID = ScopedValue.newInstance();

  /** Current member's org role ("owner" or "member"). Bound by MemberFilter. */
  public static final ScopedValue<String> ORG_ROLE = ScopedValue.newInstance();

  /** Authenticated customer's UUID. Bound by PortalAuthFilter for portal requests. */
  public static final ScopedValue<UUID> CUSTOMER_ID = ScopedValue.newInstance();

  /** JWT group memberships (e.g., "platform-admins"). Bound by platform admin filter. */
  public static final ScopedValue<Set<String>> GROUPS = ScopedValue.newInstance();

  public static final String DEFAULT_TENANT = "public";

  private static final String PLATFORM_ADMINS_GROUP = "platform-admins";

  /** Returns the current member's UUID. Throws if not bound by filter chain. */
  public static UUID requireMemberId() {
    if (!MEMBER_ID.isBound()) {
      throw new IllegalStateException("Member context not available — MEMBER_ID not bound");
    }
    return MEMBER_ID.get();
  }

  /** Returns the tenant schema name. Throws if not bound by filter chain. */
  public static String requireTenantId() {
    if (!TENANT_ID.isBound()) {
      throw new IllegalStateException("Tenant context not available — TENANT_ID not bound");
    }
    return TENANT_ID.get();
  }

  /** Returns the org ID (Keycloak alias / slug). Throws if not bound by filter chain. */
  public static String requireOrgId() {
    if (!ORG_ID.isBound()) {
      throw new IllegalStateException("Org context not available — ORG_ID not bound");
    }
    return ORG_ID.get();
  }

  /** Returns the current member's org role, or null if not bound. */
  public static String getOrgRole() {
    return ORG_ROLE.isBound() ? ORG_ROLE.get() : null;
  }

  /** Requires the current member to have the "owner" org role. Throws ForbiddenException if not. */
  public static void requireOwner() {
    if (!"owner".equals(getOrgRole())) {
      throw new ForbiddenException(
          "Owner required", "Only the organization owner can perform this action");
    }
  }

  /** Returns the JWT groups, or an empty set if not bound. */
  public static Set<String> getGroups() {
    return GROUPS.isBound() ? GROUPS.get() : Collections.emptySet();
  }

  /** Returns true if the current request has the platform-admins group. */
  public static boolean isPlatformAdmin() {
    return getGroups().contains(PLATFORM_ADMINS_GROUP);
  }

  /** Returns the tenant schema name, or null if not bound. */
  public static String getTenantIdOrNull() {
    return TENANT_ID.isBound() ? TENANT_ID.get() : null;
  }

  private RequestScopes() {}
}
