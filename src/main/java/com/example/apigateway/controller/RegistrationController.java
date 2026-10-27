package com.example.apigateway.controller;

import com.example.apigateway.dto.RegistrationDtos.*;
import com.example.apigateway.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {

    private final RegistrationService registrations;

    public RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<RegistrationResponse>> register(
            @Valid @RequestBody RegistrationRequest request
    ) {
        return registrations.register(request)
                .map(response -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .cacheControl(CacheControl.noStore())
                        .body(response)
                );
    }
}