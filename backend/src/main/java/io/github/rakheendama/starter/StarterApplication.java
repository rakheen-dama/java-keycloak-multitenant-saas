package io.github.rakheendama.starter;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class StarterApplication {

  public static void main(String[] args) {
    SpringApplication.run(StarterApplication.class, args);
  }

  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
