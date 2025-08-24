package com.project.event_ticket_backend.user.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "otp.email-verification")
@Data
public class OtpProperties {
    private String cachePrefix;
    private Duration ttl;
    private String characters;
    private int length;
}
