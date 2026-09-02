package com.tickify.user.controller;

import com.tickify.user.dto.UserProfileResponseDto;
import com.tickify.user.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/email")
@RequiredArgsConstructor
@Tag(name = "E-mail verification")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @Operation(summary = "Re-send the verification link",
            description = "Always returns 204, whether or not the address is registered — "
                    + "an anonymous caller must not be able to probe for accounts.")
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerificationLink(@RequestParam String email) {
        emailVerificationService.reSendVerificationToken(email);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Verify an e-mail address",
            description = "Redeems the single-use token from the verification link.")
    @GetMapping("/verify")
    public ResponseEntity<UserProfileResponseDto> verifyEmail(
            @RequestParam("t") String token,
            // Older links also carried an encrypted user id. The token alone identifies the
            // user now, so this is accepted and ignored rather than rejected as unknown.
            @RequestParam(value = "uid", required = false) String legacyUserId) {

        return ResponseEntity.ok(emailVerificationService.verifyEmail(token));
    }
}
