package com.support.hub.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Validation failed");
        response.put("errors", errors);
        response.put("status", HttpStatus.BAD_REQUEST.value());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Authentication failed");
        response.put("error", ex.getMessage());
        response.put("status", HttpStatus.UNAUTHORIZED.value());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationCredentialsNotFoundException(
            AuthenticationCredentialsNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Authentication credentials not found");
        response.put("error", ex.getMessage());
        response.put("status", HttpStatus.UNAUTHORIZED.value());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflictException(
            ConflictException ex) {
        log.warn("Conflict detected: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Conflict");
        response.put("error", ex.getMessage());
        response.put("status", HttpStatus.CONFLICT.value());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.support.customer.exception.ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerConflictException(
            com.support.customer.exception.ConflictException ex) {
        log.warn("Conflict detected: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Conflict");
        response.put("error", ex.getMessage());
        response.put("status", HttpStatus.CONFLICT.value());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(com.support.customer.exception.ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerResourceNotFoundException(
            com.support.customer.exception.ResourceNotFoundException ex) {
        log.warn("Customer resource not found: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("status", HttpStatus.NOT_FOUND.value());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(com.support.ticket.exception.ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTicketResourceNotFoundException(
            com.support.ticket.exception.ResourceNotFoundException ex) {
        log.warn("Ticket resource not found: {}", ex.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("status", HttpStatus.NOT_FOUND.value());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        
        String parameterName = ex.getName();
        Object providedValue = ex.getValue();
        Class<?> requiredType = ex.getRequiredType();
        
        Map<String, Object> response = new HashMap<>();
        
        if (requiredType != null && requiredType.isEnum()) {
            Object[] enumConstants = requiredType.getEnumConstants();
            String[] validValues = new String[enumConstants.length];
            for (int i = 0; i < enumConstants.length; i++) {
                validValues[i] = enumConstants[i].toString();
            }
            
            response.put("message", "Invalid value for parameter '" + parameterName + "'");
            response.put("error", "Invalid enum value: '" + providedValue + "'");
            response.put("parameter", parameterName);
            response.put("providedValue", providedValue);
            response.put("validValues", validValues);
            response.put("status", HttpStatus.BAD_REQUEST.value());
            
            log.warn("Invalid enum value for parameter '{}': '{}'. Valid values: {}", 
                    parameterName, providedValue, String.join(", ", validValues));
        } else {
            response.put("message", "Invalid value for parameter '" + parameterName + "'");
            response.put("error", "Cannot convert '" + providedValue + "' to " + 
                    (requiredType != null ? requiredType.getSimpleName() : "required type"));
            response.put("parameter", parameterName);
            response.put("providedValue", providedValue);
            response.put("status", HttpStatus.BAD_REQUEST.value());
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}

