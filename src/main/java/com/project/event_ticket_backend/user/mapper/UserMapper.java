package com.project.event_ticket_backend.user.mapper;

import com.project.event_ticket_backend.user.dto.UserProfileResponseDto;
import com.project.event_ticket_backend.user.entity.Role;
import com.project.event_ticket_backend.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserProfileResponseDto toUserProfileResponseDto(final User user) {
        Set<String> roles = user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());

        return new UserProfileResponseDto(user.getEmail(), roles, user.isEmailVerified());
    }
}

