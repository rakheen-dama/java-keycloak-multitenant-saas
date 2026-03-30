package io.github.rakheendama.starter.portal;

import io.github.rakheendama.starter.customer.CustomerRepository;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMapping;
import io.github.rakheendama.starter.multitenancy.OrgSchemaMappingRepository;
import io.github.rakheendama.starter.multitenancy.RequestScopes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal/auth")
public class PortalAuthController {

  private static final Logger log = LoggerFactory.getLogger(PortalAuthController.class);
  private static final String GENERIC_MESSAGE = "If an account exists, a link has been sent.";

  private final MagicLinkService magicLinkService;
  private final PortalJwtService portalJwtService;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final CustomerRepository customerRepository;

  public PortalAuthController(
      MagicLinkService magicLinkService,
      PortalJwtService portalJwtService,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      CustomerRepository customerRepository) {
    this.magicLinkService = magicLinkService;
    this.portalJwtService = portalJwtService;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.customerRepository = customerRepository;
  }

  record RequestLinkRequest(@NotBlank @Email String email, @NotBlank String orgId) {}

  record MessageResponse(String message) {}

  record ExchangeRequest(@NotBlank String token) {}

  record TokenResponse(String token, UUID customerId, String customerName) {}

  @PostMapping("/request-link")
  public ResponseEntity<MessageResponse> requestLink(
      @Valid @RequestBody RequestLinkRequest body, HttpServletRequest request) {
    String schema =
        orgSchemaMappingRepository
            .findByOrgId(body.orgId())
            .map(OrgSchemaMapping::getSchemaName)
            .orElse(null);
    if (schema == null) {
      return ResponseEntity.ok(new MessageResponse(GENERIC_MESSAGE));
    }

    try {
      ScopedValue.where(RequestScopes.TENANT_ID, schema)
          .run(
              () -> {
                var customerOpt = customerRepository.findByEmail(body.email());
                if (customerOpt.isEmpty()) {
                  return;
                }
                var customer = customerOpt.get();
                if (!"ACTIVE".equals(customer.getStatus())) {
                  return;
                }
                magicLinkService.generateToken(
                    customer.getId(), body.orgId(), request.getRemoteAddr());
              });
    } catch (TooManyRequestsException e) {
      throw e; // Re-throw for GlobalExceptionHandler to map to 429
    } catch (Exception e) {
      log.warn("Error during magic link request for org {}", body.orgId(), e);
    }

    return ResponseEntity.ok(new MessageResponse(GENERIC_MESSAGE));
  }

  @PostMapping("/exchange")
  public ResponseEntity<TokenResponse> exchange(@Valid @RequestBody ExchangeRequest body) {
    // MagicLinkToken is in public schema — no scoped value needed for token lookup
    var result = magicLinkService.exchangeToken(body.token());

    String schema =
        orgSchemaMappingRepository
            .findByOrgId(result.orgId())
            .map(OrgSchemaMapping::getSchemaName)
            .orElse(null);
    if (schema == null) {
      throw new PortalAuthException("Organization not found");
    }

    // Verify customer is still ACTIVE in the tenant schema
    var customer =
        ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .call(() -> customerRepository.findById(result.customerId()));
    if (customer.isEmpty() || !"ACTIVE".equals(customer.get().getStatus())) {
      throw new PortalAuthException("Customer account is not active");
    }

    String portalJwt = portalJwtService.issueToken(result.customerId(), result.orgId());
    return ResponseEntity.ok(
        new TokenResponse(portalJwt, result.customerId(), customer.get().getName()));
  }
}
