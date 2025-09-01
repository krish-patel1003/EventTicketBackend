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

    Optional<QRCode> findByQrCode(String qrCode);  // ✅ match entity field name exactly

    Optional<QRCode> findByBooking_Id(UUID bookingId);

    @Query("SELECT q FROM QRCode q WHERE q.qrCode = :qrCode AND q.isValid = true AND q.used = false")
    Optional<QRCode> findValidUnusedQRCode(@Param("qrCode") String qrCode);

    @Modifying
    @Query("UPDATE QRCode q SET q.used = true WHERE q.id = :id")
    void markAsUsed(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE QRCode q SET q.isValid = false WHERE q.booking.id = :bookingId")
    void invalidateByBookingId(@Param("bookingId") UUID bookingId);

}
