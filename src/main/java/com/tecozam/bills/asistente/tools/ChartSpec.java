package com.tecozam.bills.asistente.tools;

import java.util.List;
import java.util.Map;

public record ChartSpec(
    String type,
    String title,
    String subtitle,
    List<Map<String, Object>> data,
    Map<String, Object> config
) {}
