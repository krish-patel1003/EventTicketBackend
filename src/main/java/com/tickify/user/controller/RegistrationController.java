package com.tickify.user.controller;

import com.tickify.user.dto.RegisterRequestDto;
import com.tickify.user.dto.RegisterResponseDto;
import com.tickify.user.mapper.UserRegistrationMapper;
import com.tickify.user.service.EmailVerificationService;
import com.tickify.user.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RegistrationController {

    @Value("${email-verification.required}")
    private boolean emailVerificationRequired;
    private final UserRegistrationService userRegistrationService;
    private final EmailVerificationService emailVerificationService;
    private final UserRegistrationMapper userRegistrationMapper;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> registerDto(
            @Valid @RequestBody final RegisterRequestDto registerRequestDto) {


        log.info("dto requested roles: {}", registerRequestDto.requestedRoles());

        final var registeredUser = userRegistrationService.registerUser(
                userRegistrationMapper.toEntity(registerRequestDto), registerRequestDto.requestedRoles());

        if(emailVerificationRequired) {
            emailVerificationService.sendVerificationToken(registeredUser.getId(), registeredUser.getEmail());
        }

        return ResponseEntity.ok(userRegistrationMapper.toRegistrationResponseDto(registeredUser, emailVerificationRequired));
    }

}
