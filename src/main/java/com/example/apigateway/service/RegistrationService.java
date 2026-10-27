package com.example.apigateway.service;

import com.example.apigateway.client.RegistrationClient;
import com.example.apigateway.dto.RegistrationDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

@Service
public class RegistrationService {

    private static final Logger log =
            LoggerFactory.getLogger(RegistrationService.class);

    private final RegistrationClient client;

    public RegistrationService(RegistrationClient client) {
        this.client = client;
    }

    public Mono<RegistrationResponse> register(RegistrationRequest request) {
        return client.createUser(request)
                .onErrorMap(this::userCreationFailure)
                .flatMap(user ->
                        client.createCredentials(user.id(), request)
                                .thenReturn(new RegistrationResponse(
                                        user.id(),
                                        request.login().trim()
                                                .toLowerCase(Locale.ROOT),
                                        user.email()
                                ))
                                .onErrorResume(error ->
                                        handleAuthFailure(user.id(), error)
                                )
                )
                // Once started, finish this attempt even if the caller disconnects.
                .cache();
    }

    private Mono<RegistrationResponse> handleAuthFailure(
            Long userId,
            Throwable error
    ) {
        if (!isConfirmedRejection(error)) {
            log.error(
                    "Registration outcome unknown for userId={}; error={}",
                    userId,
                    error.getClass().getSimpleName()
            );

            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Registration outcome is unknown; userId=" + userId
                            + " requires reconciliation"
            ));
        }

        HttpStatus status = HttpStatus.BAD_GATEWAY;
        String message = "Authentication Service rejected registration";

        if (error instanceof WebClientResponseException response) {
            int code = response.getStatusCode().value();

            if (code == 409) {
                status = HttpStatus.CONFLICT;
                message = "Login is already registered";
            } else if (code == 400 || code == 422) {
                status = HttpStatus.BAD_REQUEST;
                message = "Authentication Service rejected the credentials";
            }
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Authentication Service is unavailable";
        }

        ResponseStatusException registrationError =
                new ResponseStatusException(
                        status,
                        message + "; created user was rolled back"
                );

        return client.deleteUser(userId)
                .retryWhen(
                        Retry.backoff(2, Duration.ofMillis(200))
                                .filter(this::isTransientFailure)
                                .onRetryExhaustedThrow(
                                        (specification, signal) -> signal.failure()
                                )
                )
                .onErrorMap(rollbackError -> {
                    log.error(
                            "Registration rollback failed for userId={}; error={}",
                            userId,
                            rollbackError.getClass().getSimpleName()
                    );

                    return new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Registration rollback failed; userId=" + userId
                                    + " requires reconciliation"
                    );
                })
                .then(Mono.<RegistrationResponse>error(registrationError));
    }

    private boolean isConfirmedRejection(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            int code = response.getStatusCode().value();

            return code == 400
                    || code == 401
                    || code == 403
                    || code == 404
                    || code == 405
                    || code == 409
                    || code == 422;
        }

        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException
                    || cause instanceof UnknownHostException) {
                return true;
            }
        }

        return false;
    }

    private boolean isTransientFailure(Throwable error) {
        return error instanceof WebClientRequestException
                || error instanceof TimeoutException
                || (error instanceof WebClientResponseException response
                && response.getStatusCode().is5xxServerError());
    }

    private Throwable userCreationFailure(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            int code = response.getStatusCode().value();

            if (code == 409) {
                return new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Email is already registered"
                );
            }

            if (code == 400) {
                return new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "User profile validation failed"
                );
            }

            if (code == 401 || code == 403 || code == 404) {
                return new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "User Service registration endpoint is unavailable"
                );
            }
        }

        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "User Service did not confirm creation; check its data before retrying"
        );
    }
}