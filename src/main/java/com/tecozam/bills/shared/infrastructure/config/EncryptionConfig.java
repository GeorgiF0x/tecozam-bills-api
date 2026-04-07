package com.tecozam.bills.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Configuration
public class EncryptionConfig {

    // Default is Base64 of exactly 32 bytes: "TecozamBills2026DevSecretKey32B!"
    @Value("${app.aes.secret-key:VGVjb3phbUJpbGxzMjAyNkRldlNlY3JldEtleTMyQiE=}")
    private String aesSecretKey;

    @Bean
    public SecretKey aesSecretKey() {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(aesSecretKey);
        } catch (IllegalArgumentException e) {
            // If not valid Base64, hash the raw string to get 32 bytes
            keyBytes = sha256(aesSecretKey);
        }

        if (keyBytes.length != 32) {
            // Ensure exactly 32 bytes via SHA-256
            keyBytes = sha256(new String(keyBytes, StandardCharsets.UTF_8));
        }

        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
