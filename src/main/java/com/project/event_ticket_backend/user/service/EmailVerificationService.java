package com.project.event_ticket_backend.user.service;

import com.project.event_ticket_backend.user.dto.UserProfileResponseDto;
import com.project.event_ticket_backend.user.entity.User;
import com.project.event_ticket_backend.user.mapper.UserMapper;
import com.project.event_ticket_backend.user.repository.UserRepository;
import com.project.event_ticket_backend.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.GONE;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    @Value("${email-verification.base-url}")
    private String baseUrl;
    private final OtpService otpService;
    private final CryptoUtil cryptoUtil;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final UserMapper userMapper;

    @Async
    public void sendVerificationToken(UUID userId, String email) {
        final var token = otpService.generateAndStoreOtp(userId);

        final var emailVerificationUrl = baseUrl.formatted(
                URLEncoder.encode(cryptoUtil.encrypt(userId.toString()), StandardCharsets.UTF_8),
                token);

        final var emailText = "Click the link to verify the email: " + emailVerificationUrl;

        final var message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Email Verification");
        message.setFrom("System");
        message.setText(emailText);

        mailSender.send(message);
    }

    public void reSendVerificationToken(String email) {
        userRepository.findByEmailWithRoles(email).filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(user -> sendVerificationToken(user.getId(), user.getEmail()),
                        () -> log.warn(
                                "Attempt to resend verification token for non existing or already validated email: [{}]",
                                email));
    }

    @Transactional
    public UserProfileResponseDto verifyEmail(String encryptedUserId, String token) {
        final var userId = UUID.fromString(cryptoUtil.decrypt(encryptedUserId));

        if(!otpService.isOtpValid(userId, token)) {
            throw new ResponseStatusException(BAD_REQUEST, "Token invalid or expired");
        }

        otpService.deleteOtp(userId);

        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(GONE, "The user has been deactivated or deleted"));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(BAD_REQUEST, "Email is already verified");
        }

        user.setEmailVerified(true);

        return userMapper.toUserProfileResponseDto(user);
    }
}
