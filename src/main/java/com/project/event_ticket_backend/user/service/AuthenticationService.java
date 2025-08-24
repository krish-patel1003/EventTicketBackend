package com.project.event_ticket_backend.user.service;

import com.project.event_ticket_backend.user.config.properties.JwtProperties;
import com.project.event_ticket_backend.user.dto.AuthenticationRequestDto;
import com.project.event_ticket_backend.user.dto.AuthenticationResponseDto;
import com.project.event_ticket_backend.user.entity.RefreshToken;
import com.project.event_ticket_backend.user.repository.RefreshTokenRepository;
import com.project.event_ticket_backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthenticationResponseDto authenticate(final AuthenticationRequestDto request) {
        final var authToken = UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());
        final var authentication = authenticationManager.authenticate(authToken);

        final var accessToken = jwtService.generateToken(request.email());

        final var user = userRepository.findByEmailWithRoles(request.email())
                .orElseThrow(() ->
                        new UsernameNotFoundException("User with email [%s] not found".formatted(request.email())));

        var refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        return new AuthenticationResponseDto(accessToken, refreshToken.getId());
    }

    public AuthenticationResponseDto refreshToken(UUID refreshToken) {
        final var refreshTokenEntity = refreshTokenRepository.findByIdAndExpiresAtAfter(refreshToken, Instant.now())
                .orElseThrow(() -> new BadCredentialsException("Invalid or Expired refresh token"));

        final var newAccessToken = jwtService.generateToken(refreshTokenEntity.getUser().getEmail());

        return new AuthenticationResponseDto(newAccessToken, refreshToken);
    }

    public void revokeRefreshToken(UUID refreshToken) {
        refreshTokenRepository.deleteById(refreshToken);
    }
}
