package com.tickify.user.service;

import com.tickify.user.config.properties.JwtProperties;
import com.tickify.user.entity.Role;
import com.tickify.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

    /** Claim carrying the user's roles, read back by {@code SecurityConfig}'s converter. */
    public static final String ROLES_CLAIM = "roles";
    /** Claim carrying the user's id, so request handling needs no lookup by e-mail. */
    public static final String USER_ID_CLAIM = "uid";

    private final JwtProperties jwtProperties;
    private final JwtEncoder jwtEncoder;

    /**
     * Mints an access token carrying the caller's identity <em>and</em> their roles.
     *
     * <p>Putting the roles in the token is what lets Spring's resource server authorize a
     * request on its own. The previous design authenticated with a bearer token but resolved
     * authorities from a separate database lookup on every request — two mechanisms for one
     * job, of which the resource server won, leaving every role check to fail.
     *
     * <p>The trade-off is the usual one for stateless auth: a role revoked mid-session is not
     * felt until the token expires. Access tokens live 15 minutes, which bounds that window.
     */
    public String generateToken(final User user) {
        final var issuedAt = Instant.now();

        List<String> roles = user.getRoles() == null ? List.of()
                : user.getRoles().stream().map(Role::getName).map(Enum::name).toList();

        final var claimSet = JwtClaimsSet.builder()
                .subject(user.getEmail())
                .issuer(jwtProperties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(jwtProperties.getAccessTokenTtl()))
                .claim(USER_ID_CLAIM, user.getId().toString())
                .claim(ROLES_CLAIM, roles)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claimSet)).getTokenValue();
    }
}
