package com.project.event_ticket_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EventTicketBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventTicketBackendApplication.class, args);
	}

}
