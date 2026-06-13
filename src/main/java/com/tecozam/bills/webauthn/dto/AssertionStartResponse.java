package com.tecozam.bills.webauthn.dto;

public record AssertionStartResponse(
        String token,
        String publicKeyCredentialRequestOptions
) {}
