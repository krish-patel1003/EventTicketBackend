package com.tickify.exception;

import com.tickify.dto.ErrorResponseDto;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Maps exceptions to a single error body shape.
 *
 * <p>The statuses matter to callers, not just to humans: the frontend distinguishes
 * "someone beat you to that seat" (409) from "your booking window expired" (403) from a
 * genuine server fault (500), and the load test asserts on the same distinction.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponseDto> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest request) {

        return buildResponse(ex.getStatusCode().value(), ex.getReason(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        return buildResponse(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotFound(
            UsernameNotFoundException ex, HttpServletRequest request) {

        return buildResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {

        return buildResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage(), request);
    }

    /**
     * Covers both the waiting-room gate (no active slot) and ownership checks on a booking,
     * as well as any {@code @PreAuthorize} denial that reaches the dispatcher.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        return buildResponse(HttpStatus.FORBIDDEN.value(), ex.getMessage(), request);
    }

    /**
     * Seat contention. Expected and frequent during a ticket drop, so it is logged at debug
     * and answered with 409 rather than treated as a fault.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponseDto> handleConflict(
            IllegalStateException ex, HttpServletRequest request) {

        log.debug("Booking conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(
            ValidationException ex, HttpServletRequest request) {

        return buildResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleInvalidMethodArgs(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        final var firstError = ex.getBindingResult().getFieldError();
        String message = firstError != null ? firstError.getDefaultMessage() : "Validation failed";

        return buildResponse(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    /**
     * A path or query parameter that will not convert — most often a malformed UUID.
     * Without this the type-mismatch escapes to the catch-all and reports 500 for what is
     * plainly a bad request.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

        String message = "Invalid value for parameter '%s': %s".formatted(ex.getName(), ex.getValue());
        return buildResponse(HttpStatus.BAD_REQUEST.value(), message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        return buildResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), request);
    }

    /**
     * An unmapped static path — /favicon.ico most often. This is a 404, and logging a stack
     * trace for it buries real faults in noise, so it is answered quietly.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {

        return buildResponse(HttpStatus.NOT_FOUND.value(), "No such resource", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponseDto> buildResponse(
            int status, String message, HttpServletRequest request) {

        ErrorResponseDto errorResponse = new ErrorResponseDto(
                Instant.now().toString(),
                status,
                HttpStatus.valueOf(status).getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(errorResponse);
    }
}
