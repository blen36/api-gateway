package com.example.apigateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class RegistrationDtos {

    private RegistrationDtos() {
    }

    public record RegistrationRequest(
            @NotBlank
            @Size(max = 100)
            String name,

            @NotBlank
            @Size(max = 100)
            String surname,

            @NotNull
            @Past
            LocalDate birthDate,

            @NotBlank
            @Email
            @Size(max = 320)
            String email,

            @NotBlank
            @Size(min = 3, max = 100)
            @Pattern(regexp = "[A-Za-z0-9._@+\\-]+")
            String login,

            @NotBlank
            @Size(min = 8, max = 72)
            String password
    ) {
        @AssertTrue(message = "Password must contain at most 72 UTF-8 bytes")
        public boolean isPasswordWithinBcryptLimit() {
            return password == null
                    || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }

        @Override
        public String toString() {
            return "RegistrationRequest[redacted]";
        }
    }

    public record UserCreateRequest(
            String name,
            String surname,
            LocalDate birthDate,
            String email
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserProfile(
            Long id,
            String email
    ) {
    }

    public record CredentialsRequest(
            Long userId,
            String login,
            String password
    ) {
        @Override
        public String toString() {
            return "CredentialsRequest[redacted]";
        }
    }

    public record RegistrationResponse(
            Long userId,
            String login,
            String email
    ) {
    }
}