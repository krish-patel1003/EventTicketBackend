package com.tickify.user.service;

import com.tickify.user.entity.User;
import com.tickify.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves the authenticated principal to the caller's identity.
 *
 * <p>Controllers used to inline the same SecurityContext-to-repository lookup; this keeps it
 * in one place and, for the common case, avoids the query entirely.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * The caller's id, taken straight from the {@code uid} claim.
     *
     * <p>Almost every booking endpoint needs only the id, and on the hot path — seat locking
     * during a ticket drop — a database round-trip per request to translate an e-mail into a
     * UUID is pure overhead. Falls back to a lookup for tokens issued before the claim existed.
     */
    public UUID requireId() {
        Authentication authentication = requireAuthentication();

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString(JwtService.USER_ID_CLAIM);
            if (userId != null && !userId.isBlank()) {
                return UUID.fromString(userId);
            }
        }

        return require().getId();
    }

    /** The full user row, for the cases that genuinely need more than the id. */
    @Transactional(readOnly = true)
    public User require() {
        String email = requireAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user %s not found".formatted(email)));
    }

    private Authentication requireAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UsernameNotFoundException("No authenticated user found");
        }
        return authentication;
    }
}
