package io.github.rakheendama.starter.portal;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MagicLinkService {

  private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);

  private static final int TOKEN_BYTES = 32;
  static final long TOKEN_TTL_MINUTES = 15;
  private static final int MAX_TOKENS_PER_5_MINUTES = 3;

  private final MagicLinkTokenRepository tokenRepository;
  private final TransactionTemplate transactionTemplate;
  private final Optional<JavaMailSender> mailSender;
  private final String senderAddress;
  private final SecureRandom secureRandom;

  public MagicLinkService(
      MagicLinkTokenRepository tokenRepository,
      TransactionTemplate transactionTemplate,
      Optional<JavaMailSender> mailSender,
      @Value("${starter.email.sender-address:noreply@starter.example.com}") String senderAddress) {
    this.tokenRepository = tokenRepository;
    this.transactionTemplate = transactionTemplate;
    this.mailSender = mailSender;
    this.senderAddress = senderAddress;
    try {
      this.secureRandom = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No strong SecureRandom available", e);
    }
  }

  public record ExchangeResult(UUID customerId, String orgId) {}

  /**
   * Generates a magic link token, persists its hash, and sends an email. Returns the raw token
   * (only time it exists in memory).
   */
  public String generateToken(UUID customerId, String orgId, String clientIp) {
    var result = persistToken(customerId, orgId, clientIp);

    // Send email outside the transaction (fire-and-forget)
    try {
      sendMagicLinkEmail(result.rawToken(), orgId);
    } catch (Exception e) {
      log.warn("Failed to send magic link email for customer {}", customerId, e);
    }

    return result.rawToken();
  }

  private TokenGenerationResult persistToken(UUID customerId, String orgId, String clientIp) {
    return transactionTemplate.execute(
        status -> {
          long recentCount =
              tokenRepository.countByCustomerIdAndCreatedAtAfter(
                  customerId, Instant.now().minus(5, ChronoUnit.MINUTES));
          if (recentCount >= MAX_TOKENS_PER_5_MINUTES) {
            throw new TooManyRequestsException("Too many login attempts. Please try again later.");
          }

          byte[] tokenBytes = new byte[TOKEN_BYTES];
          secureRandom.nextBytes(tokenBytes);
          String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
          String tokenHash = hashToken(rawToken);

          Instant expiresAt = Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES);
          var magicLinkToken =
              new MagicLinkToken(customerId, orgId, tokenHash, expiresAt, clientIp);
          tokenRepository.save(magicLinkToken);

          return new TokenGenerationResult(rawToken);
        });
  }

  private record TokenGenerationResult(String rawToken) {}

  /**
   * Exchanges a raw token for the associated customer and org identifiers. Marks the token as used
   * (single-use).
   */
  @Transactional
  public ExchangeResult exchangeToken(String rawToken) {
    String tokenHash = hashToken(rawToken);
    MagicLinkToken token =
        tokenRepository
            .findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> new PortalAuthException("Invalid magic link token"));

    if (token.isExpired()) {
      throw new PortalAuthException("Magic link has expired");
    }
    if (token.isUsed()) {
      throw new PortalAuthException("Magic link has already been used");
    }

    token.markUsed();
    tokenRepository.save(token);

    return new ExchangeResult(token.getCustomerId(), token.getOrgId());
  }

  static String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  private void sendMagicLinkEmail(String rawToken, String orgId) {
    if (mailSender.isEmpty()) {
      log.info("No mail sender configured — magic link email not sent");
      return;
    }
    try {
      MimeMessage message = mailSender.get().createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, false);
      helper.setFrom(senderAddress);
      helper.setTo(senderAddress); // Placeholder — real impl would resolve customer email
      helper.setSubject("Your login link");
      helper.setText(
          "Click the link below to log in to your portal:\n\n"
              + "https://portal.example.com/auth/exchange?token="
              + rawToken
              + "&org="
              + orgId
              + "\n\nThis link expires in "
              + TOKEN_TTL_MINUTES
              + " minutes.",
          false);
      mailSender.get().send(message);
      log.debug("Magic link email sent");
    } catch (Exception e) {
      log.warn("Failed to send magic link email", e);
    }
  }
}
