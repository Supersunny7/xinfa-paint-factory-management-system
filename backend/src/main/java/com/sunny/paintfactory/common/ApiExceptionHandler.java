package com.sunny.paintfactory.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Keeps business validation errors readable for the web client. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = "The operation failed. Check the input and try again";
        }
        ApiResponse<Void> body = new ApiResponse<>(
            String.valueOf(exception.getStatusCode().value()),
            message,
            null
        );
        return ResponseEntity.status(exception.getStatusCode()).body(body);
    }
}
