package com.project.event_ticket_backend.user.service;

import com.project.event_ticket_backend.user.config.JwtConfig;
import com.project.event_ticket_backend.user.config.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public String generateToken(final String email) {

        final var issuedAt = Instant.now();

        final var claimSet = JwtClaimsSet.builder()
                .subject(email)
                .issuer(jwtProperties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.getAccessTokenTtl()))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claimSet)).getTokenValue();
    }

    public String extractEmail(final String token) {
        try {
            Jwt decodeJwt = jwtDecoder.decode(token);
            return decodeJwt.getSubject();
        } catch (JwtException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isTokenValid(String token, JpaUserDetailsImpl userDetails) {
        String email = extractEmail(token);
        assert email != null;
        return email.equals(userDetails.getUsername());
    }
}
