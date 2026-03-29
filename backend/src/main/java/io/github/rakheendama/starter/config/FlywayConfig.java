package io.github.rakheendama.starter.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

  /**
   * Runs public schema migrations at startup. Spring Boot auto-Flyway is disabled
   * (spring.flyway.enabled=false in application.yml).
   */
  @Bean(initMethod = "migrate")
  public Flyway publicFlyway(DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/public")
        .schemas("public")
        .baselineOnMigrate(true)
        .load();
  }
}
