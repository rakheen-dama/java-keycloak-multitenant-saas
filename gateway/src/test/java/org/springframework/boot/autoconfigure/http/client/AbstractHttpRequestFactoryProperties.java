package org.springframework.boot.autoconfigure.http.client;

/**
 * Shim for Spring Cloud Gateway 2025.0.0 compatibility with Spring Boot 4.0.2.
 *
 * <p>Spring Cloud Gateway's {@code GatewayHttpClientEnvironmentPostProcessor} references this
 * class, which was renamed/relocated in Boot 4.0.2 final. This shim provides the expected enum to
 * avoid {@code NoClassDefFoundError} at startup. Remove when Spring Cloud Gateway is updated.
 *
 * @see <a href="https://github.com/spring-cloud/spring-cloud-gateway/issues/">Spring Cloud Gateway
 *     issues</a>
 */
public abstract class AbstractHttpRequestFactoryProperties {

  public enum Factory {
    JDK,
    HTTP_COMPONENTS,
    JETTY,
    REACTOR_NETTY,
    SIMPLE
  }
}
