package com.example.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Value("${gateway.services.user-url}") String userServiceUrl,
            @Value("${gateway.services.auth-url}") String authServiceUrl
    ) {
        return builder.routes()

                .route("authentication-service", route -> route
                        .path(
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/validate"
                        )
                        .uri(authServiceUrl))

                .route("user-service", route -> route
                        .path(
                                "/api/v1/users",
                                "/api/v1/users/**",
                                "/api/v1/cards",
                                "/api/v1/cards/**"
                        )
                        .uri(userServiceUrl))

                .build();
    }
}