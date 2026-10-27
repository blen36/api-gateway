package com.example.apigateway.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder
    ) {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter =
                new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
        );

        var reactiveConverter =
                new ReactiveJwtAuthenticationConverterAdapter(
                        authenticationConverter
                );

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                .securityContextRepository(
                        NoOpServerSecurityContextRepository.getInstance()
                )

                .requestCache(cache -> cache.requestCache(
                        NoOpServerRequestCache.getInstance()
                ))

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/register"
                        ).permitAll()

                        .pathMatchers(
                                "/internal",
                                "/internal/**",
                                "/api/v1/auth/credentials",
                                "/api/v1/auth/credentials/**"
                        ).denyAll()

                        .pathMatchers(
                                "/api/v1/users",
                                "/api/v1/users/**",
                                "/api/v1/cards",
                                "/api/v1/cards/**"
                        ).authenticated()

                        .anyExchange().authenticated()
                )

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((exchange, exception) ->
                                writeError(
                                        exchange,
                                        HttpStatus.UNAUTHORIZED,
                                        "A valid access token is required"
                                )
                        )
                        .accessDeniedHandler((exchange, exception) ->
                                writeError(
                                        exchange,
                                        HttpStatus.FORBIDDEN,
                                        "Access denied"
                                )
                        )
                )

                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt
                                .jwtDecoder(jwtDecoder)
                                .jwtAuthenticationConverter(reactiveConverter)
                        )
                        .authenticationEntryPoint((exchange, exception) ->
                                writeError(
                                        exchange,
                                        HttpStatus.UNAUTHORIZED,
                                        "Invalid or expired access token"
                                )
                        )
                        .accessDeniedHandler((exchange, exception) ->
                                writeError(
                                        exchange,
                                        HttpStatus.FORBIDDEN,
                                        "Access denied"
                                )
                        )
                )

                .build();
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ) {
        var response = exchange.getResponse();

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setCacheControl("no-store");

        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(
                    HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer"
            );
        }

        String body = "{\"status\":" + status.value()
                + ",\"message\":\"" + message + "\"}";

        var buffer = response.bufferFactory().wrap(
                body.getBytes(StandardCharsets.UTF_8)
        );

        return response.writeWith(Mono.just(buffer));
    }
}