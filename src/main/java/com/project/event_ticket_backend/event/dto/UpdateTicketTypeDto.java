package com.project.event_ticket_backend.event.dto;

public record UpdateTicketTypeDto(
        String title,
        String description,
        double price,
        int totalQuantity){
}
