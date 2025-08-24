package com.project.event_ticket_backend.exception;

import com.project.event_ticket_backend.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneric(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception", ex);
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
