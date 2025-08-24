package com.project.event_ticket_backend.user.controller;


import com.project.event_ticket_backend.user.dto.UserProfileResponseDto;
import com.project.event_ticket_backend.user.mapper.UserMapper;
import com.project.event_ticket_backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getUserProfile(final Authentication authentication) {
        final var user = userService.getUserByEmail(authentication.getName());
        return ResponseEntity.ok(userMapper.toUserProfileResponseDto(user));
    }
}
