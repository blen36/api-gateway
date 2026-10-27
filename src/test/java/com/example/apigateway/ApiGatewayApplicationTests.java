package com.example.apigateway;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.example.apigateway.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiGatewayApplicationTests {

	@TempDir
	Path tempDirectory;

	private KeyPair keyPair;
	private ReactiveJwtDecoder decoder;

	@BeforeEach
	void setUp() throws Exception {
		keyPair = generateKeyPair();

		String encodedPublicKey = Base64.getMimeEncoder(
				64,
				new byte[]{'\n'}
		).encodeToString(keyPair.getPublic().getEncoded());

		Path publicKeyFile = tempDirectory.resolve("public-key.pem");

		Files.writeString(
				publicKeyFile,
				"-----BEGIN PUBLIC KEY-----\n"
						+ encodedPublicKey
						+ "\n-----END PUBLIC KEY-----\n"
		);

		decoder = new JwtConfig().jwtDecoder(
				new FileSystemResource(publicKeyFile),
				"authentication-service",
				"java-advanced-api"
		);
	}

	@Test
	void acceptsValidAccessToken() {
		Jwt jwt = decode(token(keyPair, claims -> {
		}));

		assertNotNull(jwt);
		assertEquals("10", jwt.getSubject());
		assertEquals("USER", jwt.getClaimAsString("role"));
	}

	@Test
	void rejectsExpiredToken() {
		String token = token(keyPair, claims -> claims
				.issuedAt(Instant.now().minusSeconds(120))
				.notBefore(Instant.now().minusSeconds(120))
				.expiresAt(Instant.now().minusSeconds(60))
		);

		assertThrows(JwtException.class, () -> decode(token));
	}

	@Test
	void rejectsRefreshToken() {
		String token = token(keyPair, claims ->
				claims.claim("tokenType", "refresh")
		);

		assertThrows(JwtException.class, () -> decode(token));
	}

	@Test
	void rejectsWrongIssuer() {
		String token = token(keyPair, claims ->
				claims.issuer("another-service")
		);

		assertThrows(JwtException.class, () -> decode(token));
	}

	@Test
	void rejectsWrongAudience() {
		String token = token(keyPair, claims ->
				claims.audience(List.of("another-api"))
		);

		assertThrows(JwtException.class, () -> decode(token));
	}

	@Test
	void rejectsWrongSignature() throws Exception {
		String token = token(generateKeyPair(), claims -> {
		});

		assertThrows(JwtException.class, () -> decode(token));
	}

	@Test
	void rejectsMismatchedUserId() {
		String token = token(keyPair, claims ->
				claims.claim("userId", 20L)
		);

		assertThrows(JwtException.class, () -> decode(token));
	}

	private Jwt decode(String token) {
		return decoder.decode(token).block(Duration.ofSeconds(5));
	}

	private String token(
			KeyPair signingKeyPair,
			Consumer<JwtClaimsSet.Builder> customize
	) {
		Instant now = Instant.now();

		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer("authentication-service")
				.audience(List.of("java-advanced-api"))
				.subject("10")
				.id(UUID.randomUUID().toString())
				.issuedAt(now.minusSeconds(1))
				.notBefore(now.minusSeconds(1))
				.expiresAt(now.plusSeconds(300))
				.claim("userId", 10L)
				.claim("role", "USER")
				.claim("tokenType", "access");

		customize.accept(claims);

		NimbusJwtEncoder encoder = NimbusJwtEncoder.withKeyPair(
				(RSAPublicKey) signingKeyPair.getPublic(),
				(RSAPrivateKey) signingKeyPair.getPrivate()
		).build();

		JwsHeader header = JwsHeader
				.with(SignatureAlgorithm.RS256)
				.type("JWT")
				.build();

		return encoder.encode(
				JwtEncoderParameters.from(header, claims.build())
		).getTokenValue();
	}

	private KeyPair generateKeyPair() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}
}