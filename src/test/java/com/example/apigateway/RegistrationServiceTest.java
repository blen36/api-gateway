package com.example.apigateway;

import com.example.apigateway.client.RegistrationClient;
import com.example.apigateway.dto.RegistrationDtos.*;
import com.example.apigateway.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    RegistrationClient client;

    RegistrationService service;

    final RegistrationRequest request = new RegistrationRequest(
            "Ivan",
            "Ivanov",
            LocalDate.of(2000, 5, 15),
            "ivan@example.com",
            "ivan",
            "TestPassword123!"
    );

    @BeforeEach
    void setUp() {
        service = new RegistrationService(client);
    }

    @Test
    void createsProfileAndCredentials() {
        when(client.createUser(request))
                .thenReturn(Mono.just(new UserProfile(10L, request.email())));

        when(client.createCredentials(10L, request))
                .thenReturn(Mono.empty());

        RegistrationResponse result =
                service.register(request).block(Duration.ofSeconds(5));

        assertNotNull(result);
        assertEquals(10L, result.userId());
        assertEquals("ivan", result.login());

        verify(client).createCredentials(10L, request);
        verify(client, never()).deleteUser(anyLong());
    }

    @Test
    void rollsBackUserWhenLoginIsTaken() {
        when(client.createUser(request))
                .thenReturn(Mono.just(new UserProfile(10L, request.email())));

        when(client.createCredentials(10L, request))
                .thenReturn(Mono.error(httpError(409)));

        when(client.deleteUser(10L))
                .thenReturn(Mono.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.register(request).block(Duration.ofSeconds(5))
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(client).deleteUser(10L);
    }

    @Test
    void doesNotCallAuthWhenUserCreationFails() {
        when(client.createUser(request))
                .thenReturn(Mono.error(httpError(409)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.register(request).block(Duration.ofSeconds(5))
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(client, never()).createCredentials(anyLong(), any());
        verify(client, never()).deleteUser(anyLong());
    }

    @Test
    void reportsFailedRollback() {
        when(client.createUser(request))
                .thenReturn(Mono.just(new UserProfile(10L, request.email())));

        when(client.createCredentials(10L, request))
                .thenReturn(Mono.error(httpError(409)));

        when(client.deleteUser(10L))
                .thenReturn(Mono.error(
                        new IllegalStateException("Rollback failed")
                ));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.register(request).block(Duration.ofSeconds(5))
        );

        assertEquals(503, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("rollback failed"));
    }

    @Test
    void preservesProfileWhenAuthOutcomeIsUnknown() {
        when(client.createUser(request))
                .thenReturn(Mono.just(new UserProfile(10L, request.email())));

        when(client.createCredentials(10L, request))
                .thenReturn(Mono.error(new TimeoutException()));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.register(request).block(Duration.ofSeconds(5))
        );

        assertEquals(503, exception.getStatusCode().value());
        verify(client, never()).deleteUser(anyLong());
    }

    private WebClientResponseException httpError(int status) {
        return WebClientResponseException.create(
                status,
                "Test error",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}