package com.tickify.user.service;

import com.tickify.user.config.properties.OtpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService")
class OtpServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private OtpService otpService;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        OtpProperties properties = new OtpProperties();
        properties.setCachePrefix("otp:email-verification:%s");
        properties.setTtl(Duration.ofMinutes(5));
        properties.setCharacters("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
        properties.setLength(50);

        otpService = new OtpService(redisTemplate, properties);
    }

    @Test
    @DisplayName("keys the token to the user, so the link needs to carry only the token")
    void storesUserIdUnderTheToken() {
        String token = otpService.generateAndStoreOtp(userId);

        assertThat(token).hasSize(50);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), value.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));

        // The token is the key and the user id the value -- not the reverse. That is what lets
        // the verification link stay short enough to survive an e-mail client.
        assertThat(key.getValue()).isEqualTo("otp:email-verification:" + token);
        assertThat(value.getValue()).isEqualTo(userId.toString());
    }

    @Test
    @DisplayName("redeeming a token returns its user")
    void consumeReturnsUser() {
        when(valueOps.getAndDelete("otp:email-verification:TOKEN")).thenReturn(userId.toString());

        assertThat(otpService.consumeToken("TOKEN")).contains(userId);
    }

    @Test
    @DisplayName("redeeming deletes in the same operation, so a link cannot be used twice")
    void consumeIsSingleUse() {
        when(valueOps.getAndDelete(anyString())).thenReturn(userId.toString(), (String) null);

        assertThat(otpService.consumeToken("TOKEN")).contains(userId);
        assertThat(otpService.consumeToken("TOKEN")).isEmpty();

        // GETDEL is one round trip: two simultaneous clicks cannot both read it as present.
        verify(valueOps, org.mockito.Mockito.times(2)).getAndDelete("otp:email-verification:TOKEN");
    }

    @Test
    @DisplayName("an unknown or expired token redeems to nothing")
    void unknownTokenIsEmpty() {
        when(valueOps.getAndDelete(anyString())).thenReturn(null);

        assertThat(otpService.consumeToken("nonsense")).isEmpty();
    }

    @Test
    @DisplayName("a missing token is handled without touching Redis")
    void blankTokenIsEmpty() {
        assertThat(otpService.consumeToken(null)).isEmpty();
        assertThat(otpService.consumeToken("  ")).isEmpty();

        verify(valueOps, org.mockito.Mockito.never()).getAndDelete(anyString());
    }

    @Test
    @DisplayName("tokens are not predictable between issues")
    void tokensAreRandom() {
        assertThat(otpService.generateAndStoreOtp(userId))
                .isNotEqualTo(otpService.generateAndStoreOtp(userId));
    }
}
