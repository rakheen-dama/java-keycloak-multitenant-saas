package io.github.rakheendama.starter.multitenancy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class SchemaNameGenerator {

  private SchemaNameGenerator() {}

  /**
   * Generates a deterministic tenant schema name from a Keycloak org slug.
   *
   * <p>Algorithm: "tenant_" + first 12 hex chars of SHA-256(orgSlug)
   *
   * <p>Example: "acme-corp" -> "tenant_a3f2b1c4d5e6"
   */
  public static String generate(String orgSlug) {
    if (orgSlug == null || orgSlug.isBlank()) {
      throw new IllegalArgumentException("Org slug must not be null or blank");
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(orgSlug.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hashBytes) {
        hex.append(String.format("%02x", b));
      }
      return "tenant_" + hex.substring(0, 12);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
