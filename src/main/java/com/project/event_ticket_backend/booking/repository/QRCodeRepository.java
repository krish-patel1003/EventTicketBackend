package com.project.event_ticket_backend.booking.repository;

import com.project.event_ticket_backend.booking.entity.QRCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QRCodeRepository extends JpaRepository<QRCode, UUID> {

    Optional<QRCode> findByBooking_BookingReference(String bookingReference);

    Optional<QRCode> findByQRCode(String QRCode);

    Optional<QRCode> findByBooking_Id(UUID bookingId);

    @Query("SELECT q FROM qr_codes qr WHERE qr.qr_code = :qrCode AND qr.isValid = true AND qr.used = false")
    Optional<QRCode> findValidUnusedQRCode(@Param("qrCode") String qrCode);

    @Modifying
    @Query("UPDATE QRCode qr SET qr.used = true WHERE qr.id = :id")
    void markedAsUsed(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE QRCode qr SET qr.isValid = false WHERE q.booking_id = :bookingId")
    void invalidateByBookingId(@Param("booking_id") UUID bookingId);

}
