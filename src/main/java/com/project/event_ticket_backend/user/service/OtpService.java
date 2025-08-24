package com.project.event_ticket_backend.user.service;

import com.project.event_ticket_backend.user.config.properties.OtpProperties;
import com.rabbitmq.client.AMQP;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(OtpProperties.class)
public class OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final RedisTemplate<String, String> redisTemplate;
    private final OtpProperties otpProperties;

    public String generateAndStoreOtp(final UUID id) {
        final var otp = generateOtp(otpProperties.getCharacters(), otpProperties.getLength());
        final var cacheKey = getCacheKey(id);

        redisTemplate.opsForValue().set(cacheKey, otp, otpProperties.getTtl());

        return otp;
    }

    public boolean isOtpValid(final UUID id, final String otp) {
        String cacheKey = getCacheKey(id);

        return Objects.equals(redisTemplate.opsForValue().get(cacheKey), otp);
    }

    public void deleteOtp(final UUID id) {
        String cacheKey = getCacheKey(id);

        redisTemplate.delete(cacheKey);
    }

    private String generateOtp(String characters, int length) {
        StringBuilder otp = new StringBuilder(length);
        for (int i = 0; i <length; i++) {
            int index = SECURE_RANDOM.nextInt(characters.length());
            otp.append(characters.charAt(index));
        }

        return otp.toString();
    }

    private String getCacheKey(UUID id) {
        return otpProperties.getCachePrefix().formatted(id);
    }
}
