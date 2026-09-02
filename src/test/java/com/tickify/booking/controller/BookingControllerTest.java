package com.tickify.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickify.booking.dto.LockSeatRequestDto;
import com.tickify.booking.dto.StartPaymentRequestDto;
import com.tickify.booking.service.BookingService;
import com.tickify.exception.GlobalExceptionHandler;
import com.tickify.user.service.CurrentUserService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer contract for the checkout endpoints: request/response shapes and, above all,
 * the status codes the frontend and the load test branch on.
 *
 * <p>Security auto-configuration is excluded here so the slice tests one thing — the HTTP
 * contract. Role enforcement is covered by {@code SecurityConfigurationTest} and end-to-end
 * by {@code BookingFlowIT}.
 */
@WebMvcTest(controllers = BookingController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("BookingController")
class BookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private BookingService bookingService;
    @MockitoBean private CurrentUserService currentUserService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID seatId = UUID.randomUUID();
    private final UUID ticketTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUserService.requireId()).thenReturn(userId);
    }

    private String lockBody() throws Exception {
        return objectMapper.writeValueAsString(
                new LockSeatRequestDto(eventId, List.of(seatId), ticketTypeId));
    }

    @Test
    @DisplayName("POST /lock returns the new booking id when the seats are free")
    void lockReturnsBookingId() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.lockSeatsAndCreateBooking(any(), any())).thenReturn(bookingId);

        mockMvc.perform(post("/api/v1/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lockBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.locked").value(true));
    }

    @Test
    @DisplayName("POST /lock checks the waiting-room slot before touching any seat")
    void lockChecksWaitingRoomFirst() throws Exception {
        doThrow(new AccessDeniedException("You do not hold an active booking slot for this event"))
                .when(bookingService).ensureActiveSlot(eventId, userId);

        mockMvc.perform(post("/api/v1/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lockBody()))
                .andExpect(status().isForbidden());

        verify(bookingService, never()).lockSeatsAndCreateBooking(any(), any());
    }

    @Test
    @DisplayName("POST /lock answers 409, not 500, when another user already holds a seat")
    void lockReturnsConflictOnContention() throws Exception {
        when(bookingService.lockSeatsAndCreateBooking(any(), any()))
                .thenThrow(new IllegalStateException("Seat already locked: " + seatId));

        mockMvc.perform(post("/api/v1/bookings/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lockBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Seat already locked: " + seatId))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings/lock"));
    }

    @Test
    @DisplayName("POST /payment/initiate acknowledges without waiting for the provider")
    void initiatePaymentIsAsynchronous() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/bookings/payment/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StartPaymentRequestDto(bookingId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_REQUESTED"));

        verify(bookingService).initiatePayment(bookingId, userId);
    }

    @Test
    @DisplayName("GET /{id} maps an unknown booking to 404")
    void unknownBookingIsNotFound() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.getBooking(bookingId, userId))
                .thenThrow(new EntityNotFoundException("Booking not found"));

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /{id} maps another user's booking to 403")
    void foreignBookingIsForbidden() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(bookingService.getBooking(bookingId, userId))
                .thenThrow(new AccessDeniedException("Not your booking"));

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /my-bookings is served alongside the legacy POST form")
    void myBookingsAvailableOnBothVerbs() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/my-bookings")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/my-bookings")).andExpect(status().isOk());
    }
}
