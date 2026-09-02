package com.tickify.user.service;

import com.tickify.user.entity.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

@Data
@RequiredArgsConstructor
public class JpaUserDetailsImpl implements UserDetails {

    private final User user;

    /**
     * Whether the account may authenticate.
     *
     * <p>Tying this directly to {@code emailVerified} made {@code email-verification.required=false}
     * ineffective: verification was no longer demanded, yet every unverified account was still
     * rejected as disabled. The policy decision belongs to {@link JpaUserDetailsServiceImpl},
     * which knows whether verification is required at all, so it is passed in here.
     */
    private final boolean enabled;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() { return user.getPassword(); }

    @Override
    public String getUsername() { return user.getEmail(); }

    @Override
    public boolean isEnabled() { return enabled; }
}
