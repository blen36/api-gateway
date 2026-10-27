package com.example.apigateway.config;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
public class JwtConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${gateway.jwt.public-key}") Resource publicKeyResource,
            @Value("${gateway.jwt.issuer}") String issuer,
            @Value("${gateway.jwt.audience}") String audience
    ) throws IOException {

        RSAPublicKey publicKey;

        try (var input = publicKeyResource.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(input);
        }

        if (publicKey == null || publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalStateException(
                    "An RSA public key of at least 2048 bits is required"
            );
        }

        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withPublicKey(publicKey)
                        .signatureAlgorithm(SignatureAlgorithm.RS256)
                        .build();

        OAuth2TokenValidator<Jwt> claimsValidator =
                jwt -> validateClaims(jwt, audience);

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(Duration.ZERO),
                        new JwtIssuerValidator(issuer),
                        claimsValidator
                )
        );

        return decoder;
    }

    private OAuth2TokenValidatorResult validateClaims(
            Jwt jwt,
            String audience
    ) {
        try {
            Object userId = jwt.getClaim("userId");
            String subject = jwt.getSubject();
            String role = jwt.getClaimAsString("role");

            boolean valid =
                    userId instanceof Number
                            && subject != null
                            && subject.equals(userId.toString())
                            && Long.parseLong(subject) > 0
                            && ("ADMIN".equals(role) || "USER".equals(role))
                            && "access".equals(jwt.getClaimAsString("tokenType"))
                            && jwt.getAudience() != null
                            && jwt.getAudience().contains(audience)
                            && jwt.getIssuedAt() != null
                            && jwt.getExpiresAt() != null
                            && jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                            && jwt.getId() != null
                            && !jwt.getId().isBlank();

            if (valid) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (RuntimeException ignored) {
            // Malformed claims must produce 401, not a server error.
        }

        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                        "invalid_token",
                        "Invalid access token claims",
                        null
                )
        );
    }
}