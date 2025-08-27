package com.project.event_ticket_backend.staff.service;

import com.project.event_ticket_backend.booking.entity.EntryMethodType;
import com.project.event_ticket_backend.booking.repository.QRCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QRCodeService {

    private final QRCodeRepository qrCodeRepository;

    @Transactional
    public boolean validateAndMarkedUsed(String qrcode) {
        var q = qrCodeRepository.findValidUnusedQRCode(qrcode).orElse(null);
        if (q == null || !q.isValid() || q.isUsed()) return false;
        q.setUsed(true);
        qrCodeRepository.save(q);
        return true;
    }

    @Transactional
    public boolean validateByBookingReference(String ref) {
        var q = qrCodeRepository.findByBooking_BookingReference(ref).orElse(null);
        if (q == null || !q.isValid() || q.isUsed()) return false;
        q.setUsed(true);
        q.setMethod(EntryMethodType.BOOKING_REFERENCE);
        qrCodeRepository.save(q);
        return true;
    }
}

