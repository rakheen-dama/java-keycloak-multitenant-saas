package io.github.rakheendama.starter.accessrequest;

import io.github.rakheendama.starter.accessrequest.AccessRequestController.SubmitAccessRequestRequest;
import io.github.rakheendama.starter.accessrequest.AccessRequestController.SubmitResponse;
import io.github.rakheendama.starter.accessrequest.AccessRequestController.VerifyOtpResponse;
import io.github.rakheendama.starter.exception.InvalidStateException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessRequestService {

  private static final Logger log = LoggerFactory.getLogger(AccessRequestService.class);

  private final AccessRequestRepository accessRequestRepository;
  private final OtpService otpService;
  private final Optional<JavaMailSender> mailSender;
  private final int otpExpiryMinutes;
  private final int otpMaxAttempts;
  private final String senderAddress;

  public AccessRequestService(
      AccessRequestRepository accessRequestRepository,
      OtpService otpService,
      Optional<JavaMailSender> mailSender,
      @Value("${starter.otp.expiry-minutes:10}") int otpExpiryMinutes,
      @Value("${starter.otp.max-attempts:5}") int otpMaxAttempts,
      @Value("${starter.email.sender-address:noreply@starter.example.com}") String senderAddress) {
    this.accessRequestRepository = accessRequestRepository;
    this.otpService = otpService;
    this.mailSender = mailSender;
    this.otpExpiryMinutes = otpExpiryMinutes;
    this.otpMaxAttempts = otpMaxAttempts;
    this.senderAddress = senderAddress;
  }

  @Transactional
  public SubmitResponse submitRequest(SubmitAccessRequestRequest dto) {
    String email = dto.email().toLowerCase();

    boolean duplicateExists =
        accessRequestRepository.existsByEmailAndStatusIn(
            email, List.of("PENDING_VERIFICATION", "PENDING"));
    if (duplicateExists) {
      return new SubmitResponse(
          "If the email is valid, a verification code will be sent.", otpExpiryMinutes);
    }

    String otp = otpService.generateOtp();
    String otpHash = otpService.hashOtp(otp);

    var accessRequest =
        new AccessRequest(email, dto.fullName(), dto.organizationName(), dto.country(),
            dto.industry());
    accessRequest.setOtpHash(otpHash);
    accessRequest.setOtpExpiresAt(otpService.otpExpiresAt(otpExpiryMinutes));

    accessRequestRepository.save(accessRequest);
    sendOtpEmail(email, otp, dto.fullName(), otpExpiryMinutes);

    return new SubmitResponse(
        "If the email is valid, a verification code will be sent.", otpExpiryMinutes);
  }

  @Transactional(noRollbackFor = {InvalidStateException.class})
  public VerifyOtpResponse verifyOtp(String email, String otp) {
    String normalizedEmail = email.toLowerCase();
    Instant now = Instant.now();

    var entity =
        accessRequestRepository
            .findByEmailAndStatus(normalizedEmail, "PENDING_VERIFICATION")
            .orElseThrow(
                () -> new InvalidStateException("Verification failed", "Verification failed"));

    if (now.isAfter(entity.getOtpExpiresAt())) {
      throw new InvalidStateException("OTP expired", "Please submit a new access request");
    }

    if (entity.getOtpAttempts() >= otpMaxAttempts) {
      throw new InvalidStateException(
          "Too many attempts", "Maximum verification attempts exceeded");
    }

    entity.setOtpAttempts(entity.getOtpAttempts() + 1);
    accessRequestRepository.save(entity);

    if (!otpService.verifyOtp(otp, entity.getOtpHash())) {
      throw new InvalidStateException("Invalid OTP", "The verification code is incorrect");
    }

    entity.setStatus("PENDING");
    entity.setOtpVerifiedAt(now);
    entity.setOtpHash(null);
    accessRequestRepository.save(entity);

    return new VerifyOtpResponse("Email verified. Your request is pending review.");
  }

  private void sendOtpEmail(
      String recipientEmail, String otp, String fullName, int expiryMinutes) {
    if (mailSender.isEmpty()) {
      log.info("No mail sender configured — OTP email not sent for {}", recipientEmail);
      return;
    }
    try {
      MimeMessage message = mailSender.get().createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false);
      helper.setFrom(senderAddress);
      helper.setTo(recipientEmail);
      helper.setSubject("Your verification code");
      helper.setText(
          "Hi %s,\n\nYour verification code is: %s\n\nThis code expires in %d minutes.\n\nIf you did not request this, please ignore this email."
              .formatted(fullName, otp, expiryMinutes),
          false);
      mailSender.get().send(message);
      log.debug("OTP email sent to {}", recipientEmail);
    } catch (Exception e) {
      throw new RuntimeException("Failed to send OTP email to " + recipientEmail, e);
    }
  }
}
