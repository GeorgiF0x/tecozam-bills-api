package com.tecozam.bills.shared.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtConfig {

    /**
     * Secret key for signing JWT tokens.
     * Must be at least 256 bits (32 bytes) for HS256.
     * Set via JWT_SECRET environment variable.
     */
    private String secret;

    /**
     * Access token expiration in milliseconds.
     * Default: 900000 (15 minutes).
     */
    private long expiration = 900_000L;

    /**
     * Refresh token expiration in milliseconds.
     * Default: 604800000 (7 days).
     */
    private long refreshExpiration = 604_800_000L;
}
