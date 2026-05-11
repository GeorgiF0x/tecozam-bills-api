package com.tecozam.bills.asistente.dto;

import java.util.List;

public record ChatToolsRequest(
    String mensaje,
    List<ChatMessage> historial
) {}
