package io.github.rakheendama.starter.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.rakheendama.starter.exception.InvalidStateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccessRequestServiceTest {

  @Test
  void generateOtp_produces6Digits() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    OtpService otpService = new OtpService(encoder);

    for (int i = 0; i < 20; i++) {
      String otp = otpService.generateOtp();
      assertThat(otp).matches("^\\d{6}$");
    }
  }

  @Test
  void hashAndVerifyOtp_works() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    OtpService otpService = new OtpService(encoder);

    String otp = "482916";
    String hash = otpService.hashOtp(otp);

    assertThat(otpService.verifyOtp(otp, hash)).isTrue();
    assertThat(otpService.verifyOtp("000000", hash)).isFalse();
  }

  @Mock private AccessRequestRepository accessRequestRepository;
  @Mock private OtpService otpService;
  @InjectMocks private AccessRequestServiceTestable accessRequestService;

  /**
   * Testable subclass that avoids the @Value injection issue with Mockito @InjectMocks. We use a
   * concrete service instance for tests that need it.
   */
  static class AccessRequestServiceTestable extends AccessRequestService {
    AccessRequestServiceTestable(
        AccessRequestRepository accessRequestRepository, OtpService otpService) {
      super(accessRequestRepository, otpService, Optional.empty(), 10, 5,
          "noreply@starter.example.com");
    }
  }

  @Test
  void submitRequest_createsPendingVerificationEntity() {
    when(accessRequestRepository.existsByEmailAndStatusIn(anyString(), anyList()))
        .thenReturn(false);
    when(otpService.generateOtp()).thenReturn("123456");
    when(otpService.hashOtp("123456")).thenReturn("$2a$hashed");
    when(otpService.otpExpiresAt(10)).thenReturn(Instant.now().plus(10, ChronoUnit.MINUTES));
    when(accessRequestRepository.save(any(AccessRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var dto =
        new AccessRequestController.SubmitAccessRequestRequest(
            "test@example.com", "Test User", "Test Corp", "South Africa", "Accounting");

    var response = accessRequestService.submitRequest(dto);

    assertThat(response.message())
        .isEqualTo("If the email is valid, a verification code will be sent.");
    assertThat(response.expiresInMinutes()).isEqualTo(10);

    ArgumentCaptor<AccessRequest> captor = ArgumentCaptor.forClass(AccessRequest.class);
    verify(accessRequestRepository).save(captor.capture());

    AccessRequest saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo("PENDING_VERIFICATION");
    assertThat(saved.getOtpHash()).isEqualTo("$2a$hashed");
    assertThat(saved.getEmail()).isEqualTo("test@example.com");
  }

  @Test
  void verifyOtp_transitionsToPending() {
    var entity =
        new AccessRequest(
            "test@example.com", "Test User", "Test Corp", "South Africa", "Accounting");
    entity.setOtpHash("$2a$hashed");
    entity.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    entity.setOtpAttempts(0);

    when(accessRequestRepository.findByEmailAndStatus("test@example.com", "PENDING_VERIFICATION"))
        .thenReturn(Optional.of(entity));
    when(otpService.verifyOtp("123456", "$2a$hashed")).thenReturn(true);
    when(accessRequestRepository.save(any(AccessRequest.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = accessRequestService.verifyOtp("test@example.com", "123456");

    assertThat(response.message()).isEqualTo("Email verified. Your request is pending review.");
    assertThat(entity.getStatus()).isEqualTo("PENDING");
    assertThat(entity.getOtpHash()).isNull();
    assertThat(entity.getOtpVerifiedAt()).isNotNull();
  }

  @Test
  void verifyOtp_maxAttemptsEnforced() {
    var entity =
        new AccessRequest(
            "test@example.com", "Test User", "Test Corp", "South Africa", "Accounting");
    entity.setOtpHash("$2a$hashed");
    entity.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    entity.setOtpAttempts(5);

    when(accessRequestRepository.findByEmailAndStatus("test@example.com", "PENDING_VERIFICATION"))
        .thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> accessRequestService.verifyOtp("test@example.com", "123456"))
        .isInstanceOf(InvalidStateException.class)
        .satisfies(
            ex -> {
              var ise = (InvalidStateException) ex;
              assertThat(ise.getBody().getTitle()).isEqualTo("Too many attempts");
            });
  }
}
