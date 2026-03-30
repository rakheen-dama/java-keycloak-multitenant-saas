package io.github.rakheendama.starter.portal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PortalJwtService {

  private static final Duration SESSION_TTL = Duration.ofHours(1);
  private final byte[] secret;

  public PortalJwtService(
      @Value("${portal.jwt.secret:change-me-in-production-secret-key-32chars}") String portalJwtSecret) {
    this.secret = portalJwtSecret.getBytes(StandardCharsets.UTF_8);
  }

  public record PortalClaims(UUID customerId, String orgId) {}

  public String issueToken(UUID customerId, String orgId) {
    try {
      Instant now = Instant.now();
      var claims =
          new JWTClaimsSet.Builder()
              .jwtID(UUID.randomUUID().toString())
              .subject(customerId.toString())
              .claim("org_id", orgId)
              .claim("type", "customer")
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(SESSION_TTL)))
              .build();
      var signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      JWSSigner signer = new MACSigner(secret);
      signedJwt.sign(signer);
      return signedJwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign portal JWT", e);
    }
  }

  public PortalClaims validateToken(String token) {
    try {
      var signedJwt = SignedJWT.parse(token);
      JWSVerifier verifier = new MACVerifier(secret);
      if (!signedJwt.verify(verifier)) {
        throw new PortalAuthException("Invalid portal token signature");
      }
      var claims = signedJwt.getJWTClaimsSet();
      if (claims.getExpirationTime() == null
          || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
        throw new PortalAuthException("Portal session has expired");
      }
      String type = claims.getStringClaim("type");
      if (!"customer".equals(type)) {
        throw new PortalAuthException("Invalid token type for portal access");
      }
      UUID customerId = UUID.fromString(claims.getSubject());
      String orgId = claims.getStringClaim("org_id");
      return new PortalClaims(customerId, orgId);
    } catch (ParseException | JOSEException e) {
      throw new PortalAuthException("Invalid portal token: " + e.getMessage());
    }
  }
}
