package com.project.event_ticket_backend.user.controller;

import com.project.event_ticket_backend.user.dto.AuthenticationRequestDto;
import com.project.event_ticket_backend.user.dto.AuthenticationResponseDto;
import com.project.event_ticket_backend.user.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponseDto> authenticate(@Valid @RequestBody final AuthenticationRequestDto request) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthenticationResponseDto> refreshToken(@RequestParam UUID refreshToken) {
        return ResponseEntity.ok(authenticationService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> revokeToken(@RequestParam UUID refreshToken) {
        authenticationService.revokeRefreshToken(refreshToken);
        return ResponseEntity.noContent().build();
    }

}
