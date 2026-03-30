package io.github.rakheendama.starter.accessrequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access-requests")
public class AccessRequestController {

  private final AccessRequestService accessRequestService;

  public AccessRequestController(AccessRequestService accessRequestService) {
    this.accessRequestService = accessRequestService;
  }

  @PostMapping
  public ResponseEntity<SubmitResponse> submitRequest(
      @Valid @RequestBody SubmitAccessRequestRequest request) {
    return ResponseEntity.ok(accessRequestService.submitRequest(request));
  }

  @PostMapping("/verify")
  public ResponseEntity<VerifyOtpResponse> verifyOtp(
      @Valid @RequestBody VerifyOtpRequest request) {
    return ResponseEntity.ok(accessRequestService.verifyOtp(request.email(), request.otp()));
  }

  public record SubmitAccessRequestRequest(
      @NotBlank @Email String email,
      @NotBlank String fullName,
      @NotBlank String organizationName,
      String country,
      String industry) {}

  public record VerifyOtpRequest(
      @NotBlank @Email String email,
      @NotBlank @Pattern(regexp = "\\d{6}", message = "OTP must be a 6-digit code") String otp) {}

  public record SubmitResponse(String message, int expiresInMinutes) {}

  public record VerifyOtpResponse(String message) {}
}
