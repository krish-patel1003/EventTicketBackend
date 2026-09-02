package com.tickify.staff.service;

import com.tickify.booking.entity.EntryMethodType;
import com.tickify.booking.entity.QRCode;
import com.tickify.booking.repository.QRCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gate scanning. The property that matters is single use: a ticket that has already let
 * someone through must never let a second person through.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QRCodeService")
class QRCodeServiceTest {

    @Mock private QRCodeRepository qrCodeRepository;
    @InjectMocks private QRCodeService qrCodeService;

    private QRCode ticket(boolean valid, boolean used) {
        QRCode qr = new QRCode();
        qr.setValid(valid);
        qr.setUsed(used);
        qr.setMethod(EntryMethodType.SCAN);
        return qr;
    }

    @Test
    @DisplayName("admits a fresh ticket and burns it in the same call")
    void admitsAndBurnsFreshTicket() {
        QRCode qr = ticket(true, false);
        when(qrCodeRepository.findValidUnusedQRCode("QR-DATA")).thenReturn(Optional.of(qr));

        assertThat(qrCodeService.validateAndMarkedUsed("QR-DATA")).isTrue();

        assertThat(qr.isUsed()).isTrue();
        verify(qrCodeRepository).save(qr);
    }

    @Test
    @DisplayName("refuses a ticket that has already been scanned")
    void refusesAlreadyUsedTicket() {
        when(qrCodeRepository.findValidUnusedQRCode("QR-DATA"))
                .thenReturn(Optional.of(ticket(true, true)));

        assertThat(qrCodeService.validateAndMarkedUsed("QR-DATA")).isFalse();
        verify(qrCodeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("refuses a revoked ticket")
    void refusesInvalidatedTicket() {
        when(qrCodeRepository.findValidUnusedQRCode("QR-DATA"))
                .thenReturn(Optional.of(ticket(false, false)));

        assertThat(qrCodeService.validateAndMarkedUsed("QR-DATA")).isFalse();
    }

    @Test
    @DisplayName("refuses an unknown code rather than failing")
    void refusesUnknownCode() {
        when(qrCodeRepository.findValidUnusedQRCode("nonsense")).thenReturn(Optional.empty());

        assertThat(qrCodeService.validateAndMarkedUsed("nonsense")).isFalse();
    }

    @Test
    @DisplayName("manual booking-reference entry records how the holder got in")
    void bookingReferenceEntryRecordsMethod() {
        QRCode qr = ticket(true, false);
        when(qrCodeRepository.findByBooking_BookingReference("TICK-ABCD1234")).thenReturn(Optional.of(qr));

        assertThat(qrCodeService.validateByBookingReference("TICK-ABCD1234")).isTrue();

        assertThat(qr.isUsed()).isTrue();
        assertThat(qr.getMethod()).isEqualTo(EntryMethodType.BOOKING_REFERENCE);
    }

    @Test
    @DisplayName("a ticket already scanned at the gate cannot then be used by reference")
    void referenceEntryCannotReuseScannedTicket() {
        when(qrCodeRepository.findByBooking_BookingReference("TICK-ABCD1234"))
                .thenReturn(Optional.of(ticket(true, true)));

        assertThat(qrCodeService.validateByBookingReference("TICK-ABCD1234")).isFalse();
    }
}
