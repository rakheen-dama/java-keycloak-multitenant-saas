package io.github.rakheendama.starter.config;

import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration constants.
 *
 * <p>TenantFilter constructs its own inline Caffeine cache (5-minute TTL, 10K max entries) to avoid
 * Spring Cache proxy complications with OncePerRequestFilter. This class documents the cache
 * parameters and can be extended with Spring {@code @EnableCaching} when needed by other services.
 */
@Configuration
public class CacheConfig {

  /** TTL for org-to-schema cache entries, in minutes. */
  public static final long ORG_SCHEMA_CACHE_TTL_MINUTES = 5;

  /** Maximum number of org-to-schema cache entries. */
  public static final long ORG_SCHEMA_CACHE_MAX_SIZE = 10_000;
}
