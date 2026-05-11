package com.tecozam.bills.asistente.dto;

import java.util.List;

public record ChatMessage(
    String role,
    String content,
    String toolCallId,
    List<ToolInvocation> toolCalls
) {}
