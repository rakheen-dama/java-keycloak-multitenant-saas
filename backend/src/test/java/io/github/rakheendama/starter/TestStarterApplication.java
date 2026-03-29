package io.github.rakheendama.starter;

import org.springframework.boot.SpringApplication;

public class TestStarterApplication {

  public static void main(String[] args) {
    SpringApplication.from(StarterApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
