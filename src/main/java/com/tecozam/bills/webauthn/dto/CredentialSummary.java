package com.tecozam.bills.webauthn.dto;

import java.time.LocalDateTime;

public record CredentialSummary(
        Long id,
        String deviceName,
        LocalDateTime registeredAt,
        long signatureCount
) {}
