package io.github.rakheendama.starter.accessrequest;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OtpService {

  private final PasswordEncoder passwordEncoder;
  private final SecureRandom secureRandom;

  public OtpService(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
    try {
      this.secureRandom = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No strong SecureRandom algorithm available", e);
    }
  }

  public String generateOtp() {
    return String.format("%06d", secureRandom.nextInt(1_000_000));
  }

  public String hashOtp(String otp) {
    return passwordEncoder.encode(otp);
  }

  public boolean verifyOtp(String rawOtp, String hash) {
    return passwordEncoder.matches(rawOtp, hash);
  }

  public Instant otpExpiresAt(int expiryMinutes) {
    return Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES);
  }
}
