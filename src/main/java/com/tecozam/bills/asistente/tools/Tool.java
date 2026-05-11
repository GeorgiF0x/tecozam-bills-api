package com.tecozam.bills.asistente.tools;

import java.util.Map;

public interface Tool {
    String getName();
    String getDescription();
    Map<String, Object> getParametersSchema();
    ToolResult execute(Map<String, Object> args);
}
