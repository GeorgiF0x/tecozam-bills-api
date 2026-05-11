package com.tecozam.bills.asistente.dto;

import java.util.Map;

public record ToolInvocation(
    String id,
    String toolName,
    Map<String, Object> args,
    String resultPreview
) {}
