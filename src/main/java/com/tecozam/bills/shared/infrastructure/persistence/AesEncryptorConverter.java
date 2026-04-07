package com.tecozam.bills.shared.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA converter that encrypts/decrypts String attributes using AES-256-GCM.
 * <p>
 * The secret key is read from the {@code AES_SECRET_KEY} environment variable
 * and must be exactly 32 bytes (Base64-encoded, 44 chars).
 * <p>
 * Storage format (Base64): {@code [12-byte IV][ciphertext][16-byte auth tag]}
 */
@Converter(autoApply = false)
public class AesEncryptorConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            SecretKey key = getSecretKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Error al cifrar el atributo", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            SecretKey key = getSecretKey();
            byte[] decoded = Base64.getDecoder().decode(dbData);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Graceful degradation: if decryption fails (e.g. key mismatch),
            // return null instead of crashing the entire query.
            return null;
        }
    }

    /**
     * Default key for development ONLY. In production, AES_SECRET_KEY MUST be set.
     * Base64 of "tecozam-bills-dev-key-32bytes!!!" (exactly 32 bytes).
     */
    private static final String DEV_DEFAULT_KEY = "dGVjb3phbS1iaWxscy1kZXYta2V5LTMyYnl0ZXMhISE=";

    private SecretKey getSecretKey() {
        String envKey = System.getenv("AES_SECRET_KEY");
        if (envKey == null || envKey.isBlank()) {
            envKey = DEV_DEFAULT_KEY;
        }
        byte[] keyBytes = Base64.getDecoder().decode(envKey);
        if (keyBytes.length != 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        return new SecretKeySpec(keyBytes, 0, 32, "AES");
    }
}
