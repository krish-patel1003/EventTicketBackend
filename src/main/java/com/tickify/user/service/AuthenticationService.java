package com.tickify.user.service;

import com.tickify.user.config.properties.JwtProperties;
import com.tickify.user.dto.AuthenticationRequestDto;
import com.tickify.user.dto.AuthenticationResponseDto;
import com.tickify.user.entity.RefreshToken;
import com.tickify.user.repository.RefreshTokenRepository;
import com.tickify.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AuthenticationResponseDto authenticate(final AuthenticationRequestDto request) {
        final var authToken = UsernamePasswordAuthenticationToken
                .unauthenticated(request.email(), request.password());
        authenticationManager.authenticate(authToken);

        // Loaded with roles: they are stamped into the access token, so no request that
        // presents the token needs to come back to the database to find out who the caller is.
        final var user = userRepository.findByEmailWithRoles(request.email())
                .orElseThrow(() ->
                        new UsernameNotFoundException("User with email [%s] not found".formatted(request.email())));

        var refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        log.info("Issued access token for {}", user.getEmail());

        return new AuthenticationResponseDto(jwtService.generateToken(user), refreshToken.getId());
    }

    @Transactional(readOnly = true)
    public AuthenticationResponseDto refreshToken(UUID refreshToken) {
        final var refreshTokenEntity = refreshTokenRepository
                .findByIdAndExpiresAtAfter(refreshToken, Instant.now())
                .orElseThrow(() -> new BadCredentialsException("Invalid or Expired refresh token"));

        // Re-read through the roles-fetching query: a role granted since login must land in
        // the new access token, otherwise refreshing would silently downgrade the caller.
        final var user = userRepository.findByEmailWithRoles(refreshTokenEntity.getUser().getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid or Expired refresh token"));

        return new AuthenticationResponseDto(jwtService.generateToken(user), refreshToken);
    }

    public void revokeRefreshToken(UUID refreshToken) {
        refreshTokenRepository.deleteById(refreshToken);
    }
}
