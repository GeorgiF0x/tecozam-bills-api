package com.tecozam.bills.asistente.tools;

import java.util.Map;

public record ToolDefinition(
    String name,
    String description,
    Map<String, Object> parametersSchema
) {}
