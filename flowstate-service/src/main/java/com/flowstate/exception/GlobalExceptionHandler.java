package com.flowstate.exception;

import com.flowstate.dto.memory.MemoryResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MemoryResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = (error instanceof FieldError fieldError)
                    ? fieldError.getField()
                    : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error on {}: {}", getRequestPath(request), errors);
        return ResponseEntity.ok(buildErrorResponse("Validation Failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MemoryResponse> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", getRequestPath(request), ex);
        return ResponseEntity.ok(buildErrorResponse(
                ex.getMessage() != null ? ex.getMessage() : "Internal Server Error",
                null));
    }

    private MemoryResponse buildErrorResponse(String message, Map<String, String> validationErrors) {
        MemoryResponse.MemoryResponseBuilder builder = MemoryResponse.builder()
                .success(false)
                .message(message);

        if (validationErrors != null && !validationErrors.isEmpty()) {
            builder.value(validationErrors);
        }

        return builder.build();
    }

    private String getRequestPath(HttpServletRequest request) {
        return request != null && request.getRequestURI() != null ? request.getRequestURI() : "";
    }
}
