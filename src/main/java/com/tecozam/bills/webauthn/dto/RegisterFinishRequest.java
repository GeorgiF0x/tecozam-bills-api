package com.tecozam.bills.webauthn.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterFinishRequest(
        @NotBlank String token,
        @NotBlank String credentialJson,
        String deviceName
) {}
