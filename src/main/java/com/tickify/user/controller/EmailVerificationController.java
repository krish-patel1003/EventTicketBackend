package com.tickify.user.controller;

import com.tickify.user.dto.UserProfileResponseDto;
import com.tickify.user.entity.User;
import com.tickify.user.mapper.UserMapper;
import com.tickify.user.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    private final UserMapper userMapper;

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerificationLink(@RequestParam String email) {
        emailVerificationService.reSendVerificationToken(email);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify")
    public ResponseEntity<UserProfileResponseDto> verifyEmail(
            @RequestParam("uid") String encryptedUserId, @RequestParam("t") String token) {

        final UserProfileResponseDto verifiedUserDto = emailVerificationService.verifyEmail(encryptedUserId, token);

        return ResponseEntity.ok(verifiedUserDto);
    }
}