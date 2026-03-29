package io.github.rakheendama.starter.gateway;

import io.github.rakheendama.starter.gateway.config.BffUserInfoExtractor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff")
public class BffController {

  private static final Logger log = LoggerFactory.getLogger(BffController.class);

  public record BffUserInfo(
      boolean authenticated,
      String userId,
      String email,
      String name,
      String picture,
      String orgId,
      String orgSlug,
      List<String> groups) {

    public static BffUserInfo unauthenticated() {
      return new BffUserInfo(false, null, null, null, null, null, null, List.of());
    }
  }

  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    // Accessing .getToken() triggers deferred resolution and cookie write
    return ResponseEntity.ok(
        Map.of(
            "token", csrfToken.getToken(),
            "parameterName", csrfToken.getParameterName(),
            "headerName", csrfToken.getHeaderName()));
  }

  @GetMapping("/me")
  public ResponseEntity<BffUserInfo> me(@AuthenticationPrincipal OidcUser user) {
    if (user == null) {
      return ResponseEntity.ok(BffUserInfo.unauthenticated());
    }

    log.debug("BFF /me called for subject: {}", user.getSubject());
    BffUserInfoExtractor.OrgInfo orgInfo = BffUserInfoExtractor.extractOrgInfo(user);
    List<String> groups = extractGroups(user);

    return ResponseEntity.ok(
        new BffUserInfo(
            true,
            user.getSubject(),
            user.getEmail(),
            user.getFullName(),
            Objects.toString(user.getPicture(), ""),
            orgInfo != null ? orgInfo.id() : null,
            orgInfo != null ? orgInfo.slug() : null,
            groups));
  }

  private static List<String> extractGroups(OidcUser user) {
    Object raw = user.getClaim("groups");
    if (raw instanceof List<?> list) {
      return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
    return List.of();
  }
}
