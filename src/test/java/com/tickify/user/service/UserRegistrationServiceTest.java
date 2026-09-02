package com.tickify.user.service;

import com.tickify.user.entity.Role;
import com.tickify.user.entity.RoleType;
import com.tickify.user.entity.User;
import com.tickify.user.repository.RoleRepository;
import com.tickify.user.repository.UserRepository;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistrationService")
class UserRegistrationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;

    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        // A real encoder, so the test asserts on actual hashing rather than a stub.
        service = new UserRegistrationService(userRepository, roleRepository, new BCryptPasswordEncoder());
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private User request(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }

    private Role role(RoleType type) {
        Role role = new Role();
        role.setName(type);
        return role;
    }

    @Test
    @DisplayName("stores a bcrypt hash, never the submitted password")
    void hashesPassword() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(roleRepository.findByNameIn(anySet())).thenReturn(List.of(role(RoleType.USER)));

        service.registerUser(request("a@example.com", "hunter2"), Set.of("USER"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPassword())
                .isNotEqualTo("hunter2")
                .startsWith("$2a$");
    }

    @Test
    @DisplayName("rejects a duplicate e-mail")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.registerUser(request("a@example.com", "pw"), Set.of("USER")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Email already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("silently drops a self-requested ADMIN role instead of granting it")
    void cannotSelfRegisterAsAdmin() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        // ADMIN is filtered before the lookup, so only ORGANIZER is ever queried for.
        when(roleRepository.findByNameIn(Set.of(RoleType.ORGANIZER)))
                .thenReturn(List.of(role(RoleType.ORGANIZER)));

        service.registerUser(request("a@example.com", "pw"), Set.of("ADMIN", "ORGANIZER"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles())
                .extracting(Role::getName)
                .containsExactly(RoleType.ORGANIZER)
                .doesNotContain(RoleType.ADMIN);
    }

    @Test
    @DisplayName("falls back to USER when no roles are requested")
    void defaultsToUserRole() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(roleRepository.findByNameIn(Set.of())).thenReturn(List.of());
        when(roleRepository.findByName(RoleType.USER)).thenReturn(Optional.of(role(RoleType.USER)));

        service.registerUser(request("a@example.com", "pw"), null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles()).extracting(Role::getName).containsExactly(RoleType.USER);
    }

    @Test
    @DisplayName("rejects an unrecognised role name")
    void rejectsUnknownRole() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.registerUser(request("a@example.com", "pw"), Set.of("SUPERUSER")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid Role");
    }

    @Test
    @DisplayName("accepts role names in any casing")
    void roleNamesAreCaseInsensitive() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(roleRepository.findByNameIn(Set.of(RoleType.ORGANIZER)))
                .thenReturn(List.of(role(RoleType.ORGANIZER)));

        service.registerUser(request("a@example.com", "pw"), Set.of("organizer"));

        verify(roleRepository).findByNameIn(Set.of(RoleType.ORGANIZER));
    }
}
