package com.tecozam.bills.asistente.dto;

import com.tecozam.bills.asistente.tools.ChartSpec;

import java.util.List;

public record ChatToolsResponse(
    String respuestaTexto,
    List<ChartSpec> charts,
    List<ToolInvocation> toolsUsadas,
    List<ChatMessage> historial
) {}
