package com.tickify.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.tickify.support.ApiClient;
import com.tickify.support.IntegrationTestBase;
import com.tickify.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole customer journey, over HTTP, against real infrastructure:
 * waiting room → seat lock → asynchronous payment → confirmed ticket → gate scan.
 *
 * <p>Each step's guarantee is asserted, not just its status code — most importantly that a
 * seat cannot be locked without an admission slot, and that the ticket only becomes valid
 * once the payment saga has come back.
 */
@DisplayName("Booking flow (end to end)")
class BookingFlowIT extends IntegrationTestBase {

    @Autowired private TestRestTemplate rest;
    @Autowired private TestFixtures fixtures;

    private ApiClient organizer;
    private ApiClient customer;
    private ApiClient staff;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        organizer = new ApiClient(rest).registerAndLogin(
                "organizer-" + suffix + "@tickify.test", "Passw0rd!", Set.of("ORGANIZER"));
        customer = new ApiClient(rest).registerAndLogin(
                "customer-" + suffix + "@tickify.test", "Passw0rd!", Set.of("USER"));
        staff = new ApiClient(rest).registerAndLogin(
                "staff-" + suffix + "@tickify.test", "Passw0rd!", Set.of("STAFF"));

        venueId = fixtures.createVenue("Test Arena " + suffix, "Testville", 40);
    }

    @Test
    @DisplayName("a customer queues, books a seat, pays, and is admitted at the gate exactly once")
    void happyPath() {
        UUID eventId = createEvent();
        UUID ticketTypeId = createTicketType(eventId);
        assignTicketTypeToAllSeats(eventId, ticketTypeId);

        UUID seatId = firstAvailableSeat(eventId);

        // --- waiting room -------------------------------------------------------------
        ResponseEntity<JsonNode> join = customer.post(
                "/api/v1/booking/queue/" + eventId + "/join", null);
        assertThat(join.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(join.getBody().get("position").asLong()).isEqualTo(1L);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200))
                .until(() -> customer.get("/api/v1/booking/queue/" + eventId + "/status")
                        .getBody().get("active").asBoolean());

        // --- seat lock ----------------------------------------------------------------
        ResponseEntity<JsonNode> lock = customer.post("/api/v1/bookings/lock", Map.of(
                "eventId", eventId, "seatIds", List.of(seatId), "ticketTypeId", ticketTypeId));

        assertThat(lock.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID bookingId = UUID.fromString(lock.getBody().get("bookingId").asText());

        ResponseEntity<JsonNode> pending = customer.get("/api/v1/bookings/" + bookingId);
        assertThat(pending.getBody().get("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(pending.getBody().get("qrCode").isNull())
                .as("no ticket is issued before payment settles")
                .isTrue();

        // --- payment saga -------------------------------------------------------------
        ResponseEntity<JsonNode> payment = customer.post("/api/v1/bookings/payment/initiate",
                Map.of("bookingId", bookingId));
        assertThat(payment.getBody().get("status").asText()).isEqualTo("PAYMENT_REQUESTED");

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250))
                .until(() -> "SUCCESS".equals(customer.get("/api/v1/bookings/" + bookingId)
                        .getBody().get("paymentStatus").asText()));

        JsonNode confirmed = customer.get("/api/v1/bookings/" + bookingId).getBody();
        String bookingReference = confirmed.get("bookingReference").asText();
        assertThat(confirmed.get("qrCode").asText()).isNotBlank();
        assertThat(confirmed.get("billingAmount").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("49.99"));

        // --- gate -----------------------------------------------------------------------
        ResponseEntity<JsonNode> firstScan = staff.post("/api/v1/staff/booking",
                Map.of("bookingReference", bookingReference));
        assertThat(firstScan.getBody().get("Valid").asBoolean()).isTrue();

        ResponseEntity<JsonNode> secondScan = staff.post("/api/v1/staff/booking",
                Map.of("bookingReference", bookingReference));
        assertThat(secondScan.getBody().get("Valid").asBoolean())
                .as("a ticket must not admit a second person")
                .isFalse();
    }

    @Test
    @DisplayName("the seat map is closed to anyone who has not been admitted by the waiting room")
    void cannotLockSeatsWithoutAnAdmissionSlot() {
        UUID eventId = createEvent();
        UUID ticketTypeId = createTicketType(eventId);
        assignTicketTypeToAllSeats(eventId, ticketTypeId);
        UUID seatId = firstAvailableSeat(eventId);

        // Note: no /join call, so this customer holds no slot.
        ResponseEntity<JsonNode> lock = customer.post("/api/v1/bookings/lock", Map.of(
                "eventId", eventId, "seatIds", List.of(seatId), "ticketTypeId", ticketTypeId));

        assertThat(lock.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("only organizers may create events")
    void customersCannotCreateEvents() {
        ResponseEntity<JsonNode> response = customer.post("/api/v1/events/", eventPayload());

        assertThat(response.getStatusCode())
                .as("@PreAuthorize must actually be enforced, not merely present")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("only staff may validate tickets at the gate")
    void customersCannotValidateTickets() {
        ResponseEntity<JsonNode> response = customer.post("/api/v1/staff/booking",
                Map.of("bookingReference", "TICK-DEADBEEF"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("anonymous callers get 401, not a stack trace")
    void anonymousAccessIsRejected() {
        assertThat(rest.getForEntity("/api/v1/events/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------------------------

    private Map<String, Object> eventPayload() {
        return Map.of(
                "title", "Integration Test Show",
                "description", "end-to-end",
                "venue_id", venueId,
                "startDate", "2030-01-01T20:00:00Z",
                "endDate", "2030-01-01T23:00:00Z",
                "ticketSaleStartDate", "2029-01-01T00:00:00Z",
                "ticketSaleEndDate", "2030-01-01T19:00:00Z");
    }

    private UUID createEvent() {
        ResponseEntity<JsonNode> response = organizer.post("/api/v1/events/", eventPayload());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").asText());
    }

    private UUID createTicketType(UUID eventId) {
        ResponseEntity<JsonNode> response = organizer.post("/api/v1/ticket-types/", Map.of(
                "title", "General Admission",
                "description", "standing",
                "event_id", eventId,
                "price", "49.99",
                "totalQuantity", 40));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString(response.getBody().get("id").asText());
    }

    private void assignTicketTypeToAllSeats(UUID eventId, UUID ticketTypeId) {
        ResponseEntity<JsonNode> response = organizer.post("/api/v1/event-seats/assign-ticket-type",
                Map.of("eventId", eventId, "ticketTypeId", ticketTypeId));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UUID firstAvailableSeat(UUID eventId) {
        ResponseEntity<JsonNode> seats = customer.get("/api/v1/events/" + eventId + "/seats/available");
        assertThat(seats.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seats.getBody()).isNotEmpty();
        return UUID.fromString(seats.getBody().get(0).get("id").asText());
    }
}
