package com.tickify.user.service;

import com.tickify.user.dto.UserProfileResponseDto;
import com.tickify.user.mapper.UserMapper;
import com.tickify.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.GONE;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    @Value("${email-verification.base-url}")
    private String baseUrl;

    @Value("${tickify.notifications.from:no-reply@tickify.example}")
    private String fromAddress;

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final UserMapper userMapper;

    /**
     * Mails a verification link.
     *
     * <p>Sent as HTML with a real anchor, not as plain text. A plain-text body is folded at
     * 78 characters by the time it reaches the recipient, which silently splits a link across
     * lines; most clients then linkify only the first fragment and the token arrives truncated.
     */
    @Async
    public void sendVerificationToken(UUID userId, String email) {
        final var token = otpService.generateAndStoreOtp(userId);
        final var verificationUrl = baseUrl.formatted(token);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(email);
            helper.setFrom(fromAddress);
            helper.setSubject("Verify your Tickify e-mail address");
            helper.setText(renderEmail(verificationUrl), true);

            mailSender.send(message);
            log.info("Sent verification link to {}", email);

        } catch (Exception e) {
            // Runs on the @Async executor, so throwing here would only be swallowed by the
            // executor's handler. Log it: the account exists and the user can request another.
            log.error("Could not send the verification e-mail to {}", email, e);
        }
    }

    public void reSendVerificationToken(String email) {
        userRepository.findByEmailWithRoles(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(
                        user -> sendVerificationToken(user.getId(), user.getEmail()),
                        // Deliberately not reported to the caller: whether an address is
                        // registered, and whether it is already verified, are not facts an
                        // anonymous request should be able to probe for.
                        () -> log.info("Ignoring resend request for unknown or already-verified address"));
    }

    /**
     * Redeems a verification token.
     *
     * <p>The token is the only thing the link carries, and redeeming it is what identifies the
     * user, so an invalid or expired one is simply a bad request — never a server error.
     */
    @Transactional
    public UserProfileResponseDto verifyEmail(String token) {
        final var userId = otpService.consumeToken(token)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST,
                        "This verification link is invalid or has expired. Request a new one."));

        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(GONE,
                        "The user has been deactivated or deleted"));

        if (user.isEmailVerified()) {
            return userMapper.toUserProfileResponseDto(user);
        }

        user.setEmailVerified(true);
        log.info("Verified e-mail for user {}", user.getEmail());

        return userMapper.toUserProfileResponseDto(user);
    }

    private String renderEmail(String verificationUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8" /><title>Verify your e-mail</title></head>
                <body style="font-family:Arial,sans-serif;margin:0;padding:20px;background:#f8f8f8;color:#333;">
                <table width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="max-width:600px;margin:auto;background:#fff;border-radius:8px;overflow:hidden;">
                  <tr>
                    <td style="padding:20px;text-align:center;background:#2f6df6;color:#fff;">
                      <h2 style="margin:0;">Welcome to Tickify</h2>
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:24px;">
                      <p>Confirm your e-mail address to finish setting up your account.</p>
                      <p style="text-align:center;margin:28px 0;">
                        <a href="%s" style="background:#2f6df6;color:#fff;text-decoration:none;
                           padding:12px 22px;border-radius:8px;font-weight:bold;display:inline-block;">
                          Verify my e-mail
                        </a>
                      </p>
                      <p style="font-size:13px;color:#777;">
                        If the button does not work, copy this link into your browser:<br />
                        <span style="word-break:break-all;">%s</span>
                      </p>
                      <p style="font-size:13px;color:#777;">
                        The link expires shortly. If it does, request a new one from the sign-in page.
                      </p>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(verificationUrl, verificationUrl);
    }
}
