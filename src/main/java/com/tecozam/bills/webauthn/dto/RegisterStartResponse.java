package com.tecozam.bills.webauthn.dto;

public record RegisterStartResponse(
        String token,
        String publicKeyCredentialCreationOptions
) {}
