package com.project.event_ticket_backend.user.mapper;

import com.project.event_ticket_backend.user.dto.RegisterRequestDto;
import com.project.event_ticket_backend.user.dto.RegisterResponseDto;
import com.project.event_ticket_backend.user.entity.Role;
import com.project.event_ticket_backend.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserRegistrationMapper {

    public User toEntity(RegisterRequestDto registerRequestDto) {
        User user = new User();
        user.setEmail(registerRequestDto.email());
        user.setPassword(registerRequestDto.password());
        return user;
    }

    public RegisterResponseDto toRegistrationResponseDto(final User user, final boolean emailVerificationRequired) {
        Set<String> roles = user.getRoles()
                .stream()
                .map(role -> role.getName().name()) // RoleType -> String
                .collect(Collectors.toSet());

        return new RegisterResponseDto(
                user.getId(),
                user.getEmail(),
                emailVerificationRequired,
                roles
        );
    }
}
