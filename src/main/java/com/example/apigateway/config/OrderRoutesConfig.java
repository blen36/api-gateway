package com.example.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderRoutesConfig {

    @Bean
    public RouteLocator orderRoutes(
            RouteLocatorBuilder builder,
            @Value("${ORDER_SERVICE_URL:http://localhost:8083}") String orderUrl
    ) {
        return builder.routes()
                .route("order-service", route -> route
                        .path(
                                "/api/v1/orders",
                                "/api/v1/orders/**",
                                "/api/v1/items",
                                "/api/v1/items/**"
                        )
                        .uri(orderUrl))
                .build();
    }
}