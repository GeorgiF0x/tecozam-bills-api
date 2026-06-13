package com.tecozam.bills.webauthn.dto;

import jakarta.validation.constraints.NotBlank;

public record AssertionFinishRequest(
        @NotBlank String token,
        @NotBlank String credentialJson
) {}
