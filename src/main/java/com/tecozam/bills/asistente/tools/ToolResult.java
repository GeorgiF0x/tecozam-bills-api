package com.tecozam.bills.asistente.tools;

import java.util.Map;

public record ToolResult(
    String toolName,
    Map<String, Object> inputArgs,
    Object data,
    ChartSpec chartSpec
) {}
