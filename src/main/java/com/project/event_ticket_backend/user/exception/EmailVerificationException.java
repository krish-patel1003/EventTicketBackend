package com.project.event_ticket_backend.user.exception;

import org.springframework.security.core.AuthenticationException;

public class EmailVerificationException extends AuthenticationException {

    public EmailVerificationException(String message) {
        super(message);
    }
}
