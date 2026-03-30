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

  record ExchangeRequest(@NotBlank String token, @NotBlank String orgId) {}

  record TokenResponse(String token, UUID customerId, String customerName) {}

  @PostMapping("/request-link")
  public ResponseEntity<MessageResponse> requestLink(
      @Valid @RequestBody RequestLinkRequest body, HttpServletRequest request) {
    String schema = resolveSchema(body.orgId());
    if (schema == null) {
      return ResponseEntity.ok(new MessageResponse(GENERIC_MESSAGE));
    }

    // Resolve customer and generate token — both inside tenant scope
    try {
      ScopedValue.where(RequestScopes.TENANT_ID, schema)
          .run(
              () -> {
                customerRepository
                    .findByEmail(body.email())
                    .filter(c -> "ACTIVE".equals(c.getStatus()))
                    .ifPresent(
                        c -> magicLinkService.generateToken(c.getId(), request.getRemoteAddr()));
              });
    } catch (TooManyRequestsException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Error during magic link generation for org {}", body.orgId(), e);
    }

    return ResponseEntity.ok(new MessageResponse(GENERIC_MESSAGE));
  }

  @PostMapping("/exchange")
  public ResponseEntity<TokenResponse> exchange(@Valid @RequestBody ExchangeRequest body) {
    String schema = resolveSchema(body.orgId());
    if (schema == null) {
      throw new PortalAuthException("Organization not found");
    }

    // Exchange token and verify customer — both inside tenant scope
    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .call(
                () -> {
                  UUID customerId = magicLinkService.exchangeToken(body.token());

                  var customer = customerRepository.findById(customerId);
                  if (customer.isEmpty() || !"ACTIVE".equals(customer.get().getStatus())) {
                    throw new PortalAuthException("Customer account is not active");
                  }

                  String portalJwt = portalJwtService.issueToken(customerId, body.orgId());
                  return new TokenResponse(portalJwt, customerId, customer.get().getName());
                });

    return ResponseEntity.ok(result);
  }

  private String resolveSchema(String orgId) {
    return orgSchemaMappingRepository
        .findByOrgId(orgId)
        .map(OrgSchemaMapping::getSchemaName)
        .orElse(null);
  }
}
