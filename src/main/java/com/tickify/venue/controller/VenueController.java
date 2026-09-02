package com.tickify.venue.controller;

import com.tickify.venue.dto.VenueDto;
import com.tickify.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/venue")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping("/")
    public ResponseEntity<List<VenueDto>> getAllVenues(){
        return ResponseEntity.ok(venueService.getAllVenues());
    }
}
