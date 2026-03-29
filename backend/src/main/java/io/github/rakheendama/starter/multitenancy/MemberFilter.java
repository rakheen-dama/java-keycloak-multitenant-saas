package io.github.rakheendama.starter.multitenancy;

import io.github.rakheendama.starter.member.Member;
import io.github.rakheendama.starter.member.MemberRepository;
import io.github.rakheendama.starter.security.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MemberFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(MemberFilter.class);

  private final MemberRepository memberRepository;

  public MemberFilter(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Only run if TenantFilter already bound the tenant context
    if (!RequestScopes.TENANT_ID.isBound()) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
      filterChain.doFilter(request, response);
      return;
    }

    Jwt jwt = jwtAuth.getToken();
    String keycloakUserId = JwtUtils.extractSub(jwt);
    if (keycloakUserId == null) {
      filterChain.doFilter(request, response);
      return;
    }

    Optional<Member> memberOpt = memberRepository.findByKeycloakUserId(keycloakUserId);
    if (memberOpt.isEmpty()) {
      // T2B stub: MemberSyncService (T4) will handle first-login member creation.
      // Continue unbound — endpoints requiring MEMBER_ID will fail gracefully.
      log.debug("No member record found for Keycloak user '{}' — skipping binding", keycloakUserId);
      filterChain.doFilter(request, response);
      return;
    }

    Member member = memberOpt.get();
    ScopedFilterChain.runScoped(
        ScopedValue.where(RequestScopes.MEMBER_ID, member.getId())
            .where(RequestScopes.ORG_ROLE, member.getRole()),
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
}
