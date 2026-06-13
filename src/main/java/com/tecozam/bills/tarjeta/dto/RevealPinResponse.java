package com.tecozam.bills.tarjeta.dto;

public record RevealPinResponse(
        String pin,
        int expiresIn
) {}
