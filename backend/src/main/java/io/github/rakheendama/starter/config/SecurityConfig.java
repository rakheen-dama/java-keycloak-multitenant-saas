package io.github.rakheendama.starter.config;

import io.github.rakheendama.starter.multitenancy.MemberFilter;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMappingRepository;
import io.github.rakheendama.starter.multitenancy.TenantFilter;
import io.github.rakheendama.starter.portal.PortalAuthFilter;
import io.github.rakheendama.starter.portal.PortalJwtService;
import java.util.List;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final TenantFilter tenantFilter;
  private final MemberFilter memberFilter;
  private final PortalJwtService portalJwtService;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final Environment environment;

  public SecurityConfig(
      TenantFilter tenantFilter,
      MemberFilter memberFilter,
      PortalJwtService portalJwtService,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      Environment environment) {
    this.tenantFilter = tenantFilter;
    this.memberFilter = memberFilter;
    this.portalJwtService = portalJwtService;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.environment = environment;
  }

  @Bean
  public PortalAuthFilter portalAuthFilter() {
    return new PortalAuthFilter(portalJwtService, orgSchemaMappingRepository);
  }

  /**
   * Portal security filter chain — handles /api/portal/** paths. No OAuth2 resource server; the
   * PortalAuthFilter handles JWT validation for portal tokens. Must be ordered before the main
   * chain so it takes priority for portal paths.
   */
  @Bean
  @Order(1)
  public SecurityFilterChain portalSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/portal/**")
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/portal/auth/**").permitAll().anyRequest().permitAll())
        .addFilterBefore(portalAuthFilter(), AnonymousAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/access-requests/**")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
        .addFilterAfter(tenantFilter, BearerTokenAuthenticationFilter.class)
        .addFilterAfter(memberFilter, TenantFilter.class);

    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    List<String> origins =
        Binder.get(environment)
            .bind("cors.allowed-origins", Bindable.listOf(String.class))
            .orElse(List.of());

    var config = new CorsConfiguration();
    if (!origins.isEmpty()) {
      config.setAllowedOrigins(origins);
    }
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
