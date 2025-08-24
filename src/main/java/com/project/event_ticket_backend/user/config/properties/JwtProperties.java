package com.project.event_ticket_backend.user.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String issuer;
    private Duration refreshTokenTtl;
    private Duration accessTokenTtl;
}
