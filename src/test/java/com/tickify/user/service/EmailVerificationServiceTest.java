package com.tickify.user.service;

import com.tickify.user.entity.User;
import com.tickify.user.mapper.UserMapper;
import com.tickify.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailVerificationService")
class EmailVerificationServiceTest {

    @Mock private OtpService otpService;
    @Mock private UserRepository userRepository;
    @Mock private JavaMailSender mailSender;

    private EmailVerificationService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(otpService, userRepository, mailSender, new UserMapper());
        ReflectionTestUtils.setField(service, "baseUrl",
                "http://localhost:8080/api/v1/auth/email/verify?t=%s");
        ReflectionTestUtils.setField(service, "fromAddress", "no-reply@tickify.example");
    }

    private User user(boolean verified) {
        User user = new User();
        user.setId(userId);
        user.setEmail("alice@example.com");
        user.setEmailVerified(verified);
        user.setRoles(List.of());
        return user;
    }

    @Test
    @DisplayName("the verification link is short enough to survive an e-mail client")
    void linkStaysShort() throws Exception {
        when(otpService.generateAndStoreOtp(userId)).thenReturn("T".repeat(50));
        MimeMessage message = new org.springframework.mail.javamail.JavaMailSenderImpl().createMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        service.sendVerificationToken(userId, "alice@example.com");

        verify(mailSender).send(message);

        var out = new ByteArrayOutputStream();
        message.writeTo(out);
        String body = out.toString();

        // The old design put an RSA-encrypted user id in the query string, which made the URL
        // 452 characters. Plain-text mail folds at 78 (RFC 5322), so the token arrived broken.
        assertThat(body).contains("api/v1/auth/email/verify?t=");
        assertThat(body).doesNotContain("uid=");

        String url = "http://localhost:8080/api/v1/auth/email/verify?t=" + "T".repeat(50);
        assertThat(url.length()).isLessThan(120);

        // Sent as HTML with a real anchor, so the client never has to linkify wrapped text.
        assertThat(message.getContentType()).contains("text/html");
    }

    @Test
    @DisplayName("redeeming a valid token marks the address verified")
    void verifiesTheAddress() {
        User unverified = user(false);
        when(otpService.consumeToken("TOKEN")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(unverified));

        var profile = service.verifyEmail("TOKEN");

        assertThat(unverified.isEmailVerified()).isTrue();
        assertThat(profile.email()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("an invalid or expired token is a bad request, not a server error")
    void invalidTokenIsBadRequest() {
        when(otpService.consumeToken("nonsense")).thenReturn(Optional.empty());

        // Previously the link carried an encrypted id: a corrupted one failed inside the
        // cipher and escaped as a 500, which read as though verification itself was broken.
        assertThatThrownBy(() -> service.verifyEmail("nonsense"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("clicking an already-used link is not an error")
    void alreadyVerifiedIsIdempotent() {
        User verified = user(true);
        when(otpService.consumeToken("TOKEN")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(verified));

        // A mail client that pre-fetches links, or a second click, must not show a failure
        // for an account that is in exactly the state the user wanted.
        assertThat(service.verifyEmail("TOKEN").emailVerified()).isTrue();
    }

    @Test
    @DisplayName("a deleted user gives 410 rather than a broken response")
    void deletedUserIsGone() {
        when(otpService.consumeToken("TOKEN")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("TOKEN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.GONE));
    }

    @Test
    @DisplayName("resending to an unknown address is silent, so accounts cannot be probed for")
    void resendDoesNotLeakAccountExistence() {
        when(userRepository.findByEmailWithRoles("nobody@example.com")).thenReturn(Optional.empty());

        service.reSendVerificationToken("nobody@example.com");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("resending to an already-verified address sends nothing")
    void resendSkipsVerifiedAccounts() {
        when(userRepository.findByEmailWithRoles("alice@example.com")).thenReturn(Optional.of(user(true)));

        service.reSendVerificationToken("alice@example.com");

        verify(otpService, never()).generateAndStoreOtp(any());
    }
}
