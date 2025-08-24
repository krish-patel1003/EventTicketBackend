package com.project.event_ticket_backend.user.service;


import com.project.event_ticket_backend.user.entity.*;
import com.project.event_ticket_backend.user.repository.RoleRepository;
import com.project.event_ticket_backend.user.repository.UserRepository;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(User user, Set<String> requestRoles) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ValidationException("Email already exists");
        }

        User newUser = new User();
        newUser.setEmail(user.getEmail());
        newUser.setPassword(passwordEncoder.encode(user.getPassword()));

        Set<RoleType> validatedRoles = validateAndConvertRoles(requestRoles);
        List<Role> roles = roleRepository.findByNameIn(validatedRoles);

        if (roles.isEmpty()) {
            // fallback: assign USER role if none provided
            Role defaultUserRole = roleRepository.findByName(RoleType.USER)
                    .orElseThrow(() -> new ValidationException("Default USER role not found in DB"));
            roles = List.of(defaultUserRole);
        }

        newUser.setRoles(roles);

        log.info("Assigned roles {} to user {}", roles, newUser.getEmail());

        return userRepository.save(newUser);
    }

    private Set<RoleType> validateAndConvertRoles(Set<String> requestedRoles) {
        if (requestedRoles == null || requestedRoles.isEmpty()) {
            return Set.of();
        }

        return requestedRoles.stream()
                .map(roleStr -> {
                    try {
                        return RoleType.valueOf(roleStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new ValidationException("Invalid Role: " + roleStr);
                    }
                })
                .filter(role -> role != RoleType.ADMIN) // prevent self-registering as ADMIN
                .collect(Collectors.toSet());
    }
}
