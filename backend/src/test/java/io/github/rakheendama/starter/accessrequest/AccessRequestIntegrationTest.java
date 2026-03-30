package io.github.rakheendama.starter.accessrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.rakheendama.starter.TestcontainersConfiguration;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessRequestIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessRequestRepository accessRequestRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @MockitoBean private JavaMailSender javaMailSender;

  private static final String KNOWN_OTP = "123456";

  @BeforeEach
  void setUp() {
    accessRequestRepository.deleteAll();
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
  }

  @Test
  void submitRequest_returns200AndCreatesEntity() throws Exception {
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "jane@acme-corp.com",
                      "fullName": "Jane Smith",
                      "organizationName": "Acme Corp",
                      "country": "South Africa",
                      "industry": "Accounting"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.message").value("If the email is valid, a verification code will be sent."))
        .andExpect(jsonPath("$.expiresInMinutes").value(10));

    var entity =
        accessRequestRepository.findByEmailAndStatus("jane@acme-corp.com", "PENDING_VERIFICATION");
    assertThat(entity).isPresent();
    assertThat(entity.get().getOtpHash()).startsWith("$2");
    assertThat(entity.get().getStatus()).isEqualTo("PENDING_VERIFICATION");
  }

  @Test
  void submitRequest_duplicatePendingEmail_returnsGeneric200() throws Exception {
    // First submission
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "dupe@corp.com",
                      "fullName": "Dupe User",
                      "organizationName": "Dupe Corp",
                      "country": "South Africa",
                      "industry": "Legal"
                    }
                    """))
        .andExpect(status().isOk());

    // Second submission with same email
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "dupe@corp.com",
                      "fullName": "Dupe User",
                      "organizationName": "Dupe Corp",
                      "country": "South Africa",
                      "industry": "Legal"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.message")
                .value("If the email is valid, a verification code will be sent."));
  }

  @Test
  void submitRequest_missingFields_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/access-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "only-email@test.com"
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void verifyOtp_correctOtp_transitionsToPending() throws Exception {
    var entity = createPendingVerificationRequest("verify-ok@corp.com");

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "verify-ok@corp.com",
                      "otp": "123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Email verified. Your request is pending review."));

    var updated = accessRequestRepository.findById(entity.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo("PENDING");
    assertThat(updated.getOtpHash()).isNull();
    assertThat(updated.getOtpVerifiedAt()).isNotNull();
  }

  @Test
  void verifyOtp_incorrectOtp_returns400() throws Exception {
    createPendingVerificationRequest("wrong-otp@corp.com");

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "wrong-otp@corp.com",
                      "otp": "000000"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid OTP"));
  }

  @Test
  void verifyOtp_expiredOtp_returns400() throws Exception {
    var entity =
        new AccessRequest(
            "expired@corp.com", "Test User", "Test Corp", "South Africa", "Accounting");
    entity.setOtpHash(passwordEncoder.encode(KNOWN_OTP));
    entity.setOtpExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
    accessRequestRepository.save(entity);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "expired@corp.com",
                      "otp": "123456"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("OTP expired"));
  }

  @Test
  void verifyOtp_tooManyAttempts_returns400() throws Exception {
    var entity =
        new AccessRequest("maxed@corp.com", "Test User", "Test Corp", "South Africa", "Accounting");
    entity.setOtpHash(passwordEncoder.encode(KNOWN_OTP));
    entity.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    entity.setOtpAttempts(5);
    accessRequestRepository.save(entity);

    mockMvc
        .perform(
            post("/api/access-requests/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email": "maxed@corp.com",
                      "otp": "123456"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Too many attempts"));
  }

  private AccessRequest createPendingVerificationRequest(String email) {
    var entity = new AccessRequest(email, "Test User", "Test Corp", "South Africa", "Accounting");
    entity.setOtpHash(passwordEncoder.encode(KNOWN_OTP));
    entity.setOtpExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
    return accessRequestRepository.save(entity);
  }
}
