package com.tickify.user.mapper;

import com.tickify.user.dto.UserProfileResponseDto;
import com.tickify.user.entity.Role;
import com.tickify.user.entity.User;
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

