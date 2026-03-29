package io.github.rakheendama.starter.multitenancy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.rakheendama.starter.security.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);

  private final OrgSchemaMappingRepository mappingRepository;
  private final Cache<String, String> orgSchemaCache =
      Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(5)).build();

  public TenantFilter(OrgSchemaMappingRepository mappingRepository) {
    this.mappingRepository = mappingRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      filterChain.doFilter(request, response);
      return;
    }

    Jwt jwt = jwtAuth.getToken();
    String orgAlias = JwtUtils.extractOrgId(jwt);

    if (orgAlias == null) {
      filterChain.doFilter(request, response);
      return;
    }

    String schema = resolveSchema(orgAlias);
    if (schema == null) {
      log.warn("No OrgSchemaMapping found for org alias '{}'", orgAlias);
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Organization not provisioned");
      return;
    }

    ScopedFilterChain.runScoped(
        ScopedValue.where(RequestScopes.TENANT_ID, schema).where(RequestScopes.ORG_ID, orgAlias),
        filterChain,
        request,
        response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.startsWith("/actuator/")
        || path.startsWith("/api/access-requests")
        || path.startsWith("/api/portal/auth/");
  }

  /** Evicts the cached schema for the given org alias. Called after provisioning. */
  public void evictSchema(String orgAlias) {
    orgSchemaCache.invalidate(orgAlias);
  }

  private String resolveSchema(String orgAlias) {
    String cached = orgSchemaCache.getIfPresent(orgAlias);
    if (cached != null) {
      return cached;
    }
    String schema =
        mappingRepository.findByOrgId(orgAlias).map(OrgSchemaMapping::getSchemaName).orElse(null);
    if (schema != null) {
      orgSchemaCache.put(orgAlias, schema);
    }
    return schema;
  }
}
