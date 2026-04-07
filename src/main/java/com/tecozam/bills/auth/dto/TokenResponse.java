package com.tecozam.bills.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String rol,
        String username,
        Long expiresIn
) {
}
