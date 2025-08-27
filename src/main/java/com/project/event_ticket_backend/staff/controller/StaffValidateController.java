package com.project.event_ticket_backend.staff.controller;

import com.project.event_ticket_backend.staff.service.QRCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@PreAuthorize("HAS_STAFF")
public class StaffValidateController {

    private final QRCodeService qrCodeService;

    @PostMapping("/qr")
    public ResponseEntity<?> validateQr(@RequestBody Map<String, String> request) {
        boolean ok = qrCodeService.validateAndMarkedUsed(request.get("qrCode"));
        return ResponseEntity.ok(Map.of("Valid", ok));
    }

    @PostMapping("/booking")
    public ResponseEntity<?> validateByBookingRef(@RequestBody Map<String, String> request) {
        boolean ok = qrCodeService.validateByBookingReference(request.get("bookingReference"));
        return ResponseEntity.ok(Map.of("Valid", ok));
    }
}
