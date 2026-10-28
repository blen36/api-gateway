package com.example.apigateway.client;

import com.example.apigateway.dto.RegistrationDtos.*;
import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Component
public class RegistrationClient {

    private final WebClient users;
    private final WebClient auth;

    public RegistrationClient(
            WebClient.Builder builder,
            @Value("${gateway.services.user-url}") String userUrl,
            @Value("${gateway.services.auth-url}") String authUrl,
            @Value("${gateway.registration-key}") String registrationKey
    ) {
        if (registrationKey == null || registrationKey.length() < 32) {
            throw new IllegalArgumentException(
                    "Registration key must contain at least 32 characters"
            );
        }

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(10));

        ReactorClientHttpConnector connector =
                new ReactorClientHttpConnector(httpClient);

        users = builder.clone()
                .baseUrl(userUrl)
                .clientConnector(connector)
                .defaultHeader("X-Registration-Key", registrationKey)
                .build();

        auth = builder.clone()
                .baseUrl(authUrl)
                .clientConnector(connector)
                .defaultHeader("X-Registration-Key", registrationKey)
                .build();
    }

    public Mono<UserProfile> createUser(RegistrationRequest request) {
        UserCreateRequest body = new UserCreateRequest(
                request.name(),
                request.surname(),
                request.birthDate(),
                request.email()
        );

        return users.post()
                .uri("/internal/users")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(UserProfile.class)
                .switchIfEmpty(Mono.error(
                        new IllegalStateException("User Service returned no user")
                ))
                .map(user -> {
                    if (user.id() == null || user.id() <= 0) {
                        throw new IllegalStateException(
                                "User Service returned an invalid user ID"
                        );
                    }
                    return user;
                })
                .timeout(Duration.ofSeconds(12));
    }

    public Mono<Void> createCredentials(
            Long userId,
            RegistrationRequest request
    ) {
        CredentialsRequest body = new CredentialsRequest(
                userId,
                request.login(),
                request.password()
        );

        return auth.post()
                .uri("/api/v1/auth/credentials")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then()
                .timeout(Duration.ofSeconds(12));
    }

    public Mono<Void> deleteUser(Long userId) {
        return users.delete()
                .uri("/internal/users/{id}", userId)
                .retrieve()
                .toBodilessEntity()
                .then()
                .timeout(Duration.ofSeconds(12));
    }
}