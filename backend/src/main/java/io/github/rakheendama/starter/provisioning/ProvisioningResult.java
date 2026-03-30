package io.github.rakheendama.starter.provisioning;

public record ProvisioningResult(boolean success, String schemaName, boolean alreadyProvisioned) {

  public static ProvisioningResult success(String schemaName) {
    return new ProvisioningResult(true, schemaName, false);
  }

  public static ProvisioningResult alreadyProvisioned(String schemaName) {
    return new ProvisioningResult(true, schemaName, true);
  }
}
