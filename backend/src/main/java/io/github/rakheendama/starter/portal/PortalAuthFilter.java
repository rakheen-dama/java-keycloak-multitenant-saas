package io.github.rakheendama.starter.portal;

import io.github.rakheendama.starter.multitenancy.OrgSchemaMapping;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMappingRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import io.github.rakheendama.starter.multitenancy.ScopedFilterChain;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class PortalAuthFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final PortalJwtService portalJwtService;
  private final OrgSchemaMappingRepository mappingRepository;

  public PortalAuthFilter(
      PortalJwtService portalJwtService, OrgSchemaMappingRepository mappingRepository) {
    this.portalJwtService = portalJwtService;
    this.mappingRepository = mappingRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing portal token");
      return;
    }

    String token = authHeader.substring(BEARER_PREFIX.length());
    PortalJwtService.PortalClaims claims;
    try {
      claims = portalJwtService.validateToken(token);
    } catch (PortalAuthException e) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
      return;
    }

    String schema =
        mappingRepository
            .findByOrgId(claims.orgId())
            .map(OrgSchemaMapping::getSchemaName)
            .orElse(null);
    if (schema == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Organization not found");
      return;
    }

    var carrier =
        ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .where(RequestScopes.ORG_ID, claims.orgId())
            .where(RequestScopes.CUSTOMER_ID, claims.customerId());

    ScopedFilterChain.runScoped(carrier, filterChain, request, response);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !path.startsWith("/api/portal/") || path.startsWith("/api/portal/auth/");
  }
}
