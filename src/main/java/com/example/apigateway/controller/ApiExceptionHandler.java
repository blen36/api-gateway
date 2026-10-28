package com.example.apigateway.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<?> validation(WebExchangeBindException exception) {
        Map<String, String> fields = new LinkedHashMap<>();

        exception.getFieldErrors().forEach(error ->
                fields.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage() == null
                                ? "Invalid value"
                                : error.getDefaultMessage()
                )
        );

        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "status", 400,
                        "message", "Request validation failed",
                        "fieldErrors", fields
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException exception) {
        String message = exception.getReason() == null
                ? "Request failed"
                : exception.getReason();

        return ResponseEntity.status(exception.getStatusCode())
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "status", exception.getStatusCode().value(),
                        "message", message
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception exception) {
        return ResponseEntity.internalServerError()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "status", 500,
                        "message", "Unexpected server error"
                ));
    }
}