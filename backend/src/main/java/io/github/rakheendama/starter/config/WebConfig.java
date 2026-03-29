package io.github.rakheendama.starter.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Web configuration for Jackson serialization. CORS is handled by SecurityConfig.
 *
 * <p>Jackson 3 (Spring Boot 4) has native java.time support — no JavaTimeModule needed. This
 * customizer disables numeric timestamps so Instant serializes as ISO-8601 strings.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Bean
  public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
    return builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
