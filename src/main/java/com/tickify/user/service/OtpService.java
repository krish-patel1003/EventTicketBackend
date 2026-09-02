package com.tickify.user.service;

import com.tickify.user.config.properties.OtpProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-use, expiring tokens for e-mail verification, held in Redis.
 *
 * <p>The token <em>is</em> the credential, and the mapping runs from token to user rather
 * than the other way round. That keeps the verification link short and means it carries no
 * user identifier at all — nothing to enumerate, and nothing to tamper with.
 *
 * <p>It previously ran the other way: the link carried the user id, RSA-encrypted and
 * base64-encoded, and Redis was keyed by user id. An RSA-2048 ciphertext is always 256 bytes,
 * so the id alone became 344 characters and the whole link 452 — which plain-text e-mail
 * folds at 78 characters (RFC 5322), corrupting the parameter and making every link fail.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(OtpProperties.class)
public class OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RedisTemplate<String, String> redisTemplate;
    private final OtpProperties otpProperties;

    /** Issues a token for this user and stores it, with its TTL, against their id. */
    public String generateAndStoreOtp(final UUID userId) {
        final var token = generateOtp(otpProperties.getCharacters(), otpProperties.getLength());

        redisTemplate.opsForValue().set(cacheKey(token), userId.toString(), otpProperties.getTtl());

        return token;
    }

    /**
     * Redeems a token, returning the user it was issued to.
     *
     * <p>Deletes as it reads, so a link cannot be used twice — including by two requests
     * arriving at once, since {@code GETDEL} is a single Redis command.
     */
    public Optional<UUID> consumeToken(final String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        final var userId = redisTemplate.opsForValue().getAndDelete(cacheKey(token));

        return Optional.ofNullable(userId).map(UUID::fromString);
    }

    /** Whether a token is currently valid, without redeeming it. */
    public boolean isOtpValid(final String token) {
        return token != null && Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey(token)));
    }

    private String generateOtp(String characters, int length) {
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            otp.append(characters.charAt(SECURE_RANDOM.nextInt(characters.length())));
        }
        return otp.toString();
    }

    private String cacheKey(String token) {
        return otpProperties.getCachePrefix().formatted(token);
    }
}
