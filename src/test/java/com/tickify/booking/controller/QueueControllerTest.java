package com.tickify.booking.controller;

import com.tickify.booking.service.QueueService;
import com.tickify.exception.GlobalExceptionHandler;
import com.tickify.user.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QueueController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@DisplayName("QueueController")
class QueueControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private QueueService queueService;
    @MockitoBean private CurrentUserService currentUserService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(currentUserService.requireId()).thenReturn(userId);
    }

    @Test
    @DisplayName("joining reports the queue position and that the user is not yet admitted")
    void joinReportsPosition() throws Exception {
        when(queueService.join(eventId, userId)).thenReturn(1_234L);
        when(queueService.hasActiveSlot(eventId, userId)).thenReturn(false);

        mockMvc.perform(post("/api/v1/booking/queue/{eventId}/join", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(1234))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()));
    }

    @Test
    @DisplayName("status flips to active once the promoter has admitted the user")
    void statusReportsAdmission() throws Exception {
        when(queueService.position(eventId, userId)).thenReturn(-1L);
        when(queueService.hasActiveSlot(eventId, userId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/booking/queue/{eventId}/status", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                // Once admitted the user is no longer in the sorted set, so rank is gone.
                .andExpect(jsonPath("$.position").value(-1));
    }

    @Test
    @DisplayName("a malformed event id is a client error, not a server error")
    void malformedEventIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/booking/queue/{eventId}/status", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
