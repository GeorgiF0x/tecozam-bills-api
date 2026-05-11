package com.tecozam.bills.asistente.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecozam.bills.asistente.dto.ChatMessage;
import com.tecozam.bills.asistente.dto.ChatToolsRequest;
import com.tecozam.bills.asistente.dto.ChatToolsResponse;
import com.tecozam.bills.asistente.dto.ToolInvocation;
import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@Slf4j
public class AsistenteToolService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final int MAX_ITERATIONS = 5;

    private final Map<String, Tool> tools;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-4.1-mini}")
    private String model;

    public AsistenteToolService(List<Tool> toolList, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.tools = toolList.stream().collect(Collectors.toMap(Tool::getName, t -> t));
        log.info("[AsistenteTool] Registered tools: {}", this.tools.keySet());
    }

    public ChatToolsResponse chat(ChatToolsRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return new ChatToolsResponse(
                "El asistente no está configurado. Falta la clave OPENAI_API_KEY.",
                List.of(), List.of(), List.of()
            );
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage());

        // Add conversation history
        if (request.historial() != null) {
            for (ChatMessage msg : request.historial()) {
                messages.add(convertToOpenAiMessage(msg));
            }
        }

        // Add current user message
        messages.add(Map.of("role", "user", "content", request.mensaje()));

        List<ChartSpec> chartsAcumulados = new ArrayList<>();
        List<ToolInvocation> toolsUsadas = new ArrayList<>();

        try {
            String finalText = null;
            int iterations = 0;

            while (iterations < MAX_ITERATIONS) {
                iterations++;
                String responseJson = callOpenAI(messages);
                Map<String, Object> responseMap = objectMapper.readValue(responseJson,
                    new TypeReference<>() {});

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices == null || choices.isEmpty()) {
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> choice = choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choice.get("message");

                // Add assistant message to history
                messages.add(message);

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");

                if (toolCalls == null || toolCalls.isEmpty()) {
                    // Final text response
                    finalText = (String) message.get("content");
                    break;
                }

                // Execute each tool call
                for (Map<String, Object> toolCall : toolCalls) {
                    String callId = (String) toolCall.get("id");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                    String toolName = (String) function.get("name");
                    String argsJson = (String) function.get("arguments");

                    log.info("[AsistenteTool] Invoking tool={} callId={}", toolName, callId);

                    Map<String, Object> toolArgs;
                    try {
                        toolArgs = objectMapper.readValue(argsJson, new TypeReference<>() {});
                    } catch (Exception e) {
                        toolArgs = Map.of();
                    }

                    String resultJson;
                    ToolResult toolResult = null;

                    Tool tool = tools.get(toolName);
                    if (tool != null) {
                        try {
                            toolResult = tool.execute(toolArgs);
                            resultJson = objectMapper.writeValueAsString(toolResult.data());

                            if (toolResult.chartSpec() != null) {
                                chartsAcumulados.add(toolResult.chartSpec());
                            }
                        } catch (Exception e) {
                            log.error("[AsistenteTool] Tool execution error tool={}: {}", toolName, e.getMessage(), e);
                            resultJson = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
                        }
                    } else {
                        log.warn("[AsistenteTool] Unknown tool: {}", toolName);
                        resultJson = "{\"error\": \"Tool not found: " + toolName + "\"}";
                    }

                    String preview = resultJson.length() > 200 ? resultJson.substring(0, 200) + "..." : resultJson;
                    toolsUsadas.add(new ToolInvocation(callId, toolName, toolArgs, preview));

                    // Add tool result message
                    Map<String, Object> toolResultMessage = new LinkedHashMap<>();
                    toolResultMessage.put("role", "tool");
                    toolResultMessage.put("tool_call_id", callId);
                    toolResultMessage.put("content", resultJson);
                    messages.add(toolResultMessage);
                }
            }

            if (finalText == null) {
                finalText = "He obtenido los datos solicitados. Revisa los gráficos generados.";
            }

            // Build updated history for client (last 20 messages, excluding system)
            List<ChatMessage> historialActualizado = messages.stream()
                .skip(1) // skip system
                .map(this::convertFromOpenAiMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            // Keep only last 10
            if (historialActualizado.size() > 10) {
                historialActualizado = historialActualizado.subList(
                    historialActualizado.size() - 10, historialActualizado.size());
            }

            return new ChatToolsResponse(finalText, chartsAcumulados, toolsUsadas, historialActualizado);

        } catch (Exception e) {
            log.error("[AsistenteTool] Error in chat loop: {}", e.getMessage(), e);
            return new ChatToolsResponse(
                "Error al procesar la consulta: " + e.getMessage(),
                chartsAcumulados, toolsUsadas, List.of()
            );
        }
    }

    // ── OpenAI HTTP call ──────────────────────────────────────────────────────

    private String callOpenAI(List<Map<String, Object>> messages) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("tools", buildToolsArray());
        body.put("tool_choice", "auto");
        body.put("parallel_tool_calls", true);
        body.put("max_tokens", 2000);
        body.put("temperature", 0.2);

        String requestBody = objectMapper.writeValueAsString(body);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(OPENAI_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(60))
            .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[AsistenteTool] OpenAI returned {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error del servicio de IA (HTTP " + response.statusCode() + ")");
        }

        return response.body();
    }

    // ── Tools array for OpenAI ────────────────────────────────────────────────

    private List<Map<String, Object>> buildToolsArray() {
        return tools.values().stream().map(t -> {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", t.getName());
            function.put("description", t.getDescription());
            function.put("parameters", t.getParametersSchema());

            Map<String, Object> toolEntry = new LinkedHashMap<>();
            toolEntry.put("type", "function");
            toolEntry.put("function", function);
            return toolEntry;
        }).collect(Collectors.toList());
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    private Map<String, Object> systemMessage() {
        String systemPrompt = """
            Eres el asistente de Tecozam Bills Manager, una plataforma de control de gastos de flota.
            Tu trabajo es ayudar a administradores y gestores a explorar los datos.

            ## REGLAS DE INVOCACIÓN DE HERRAMIENTAS

            1. Cuando el usuario te pida información sobre gastos, conductores, vehículos, facturas,
               alertas o anomalías, USA LAS HERRAMIENTAS. NO inventes números.

            2. **Invoca varias herramientas en paralelo cuando la pregunta lo requiera**.
               El sistema renderiza TODOS los gráficos juntos en un BENTO GRID adaptativo,
               así que cuanto más completo mejor visualmente.

               Patrones de pregunta → herramientas a invocar:

               - "Dame un resumen / panorama / overview / dashboard completo" →
                 [get_dashboard_stats, get_gasto_por_proveedor, get_gasto_por_concepto,
                  get_evolucion_gasto, get_alertas_pendientes] (5 tools = bento muy rico)

               - "Cómo va el negocio / situación general / estado actual" →
                 [get_dashboard_stats, get_evolucion_gasto, get_gasto_por_centro_coste,
                  get_anomalias] (4 tools)

               - "Compara gasto por proveedor y por concepto" → 2 tools en paralelo

               - "Top 10 conductores y evolución del gasto" → 2 tools en paralelo

               - "Análisis del último mes" →
                 [get_gasto_por_trabajador, get_gasto_por_proveedor, get_evolucion_gasto]
                 (3 tools, todas con from/to del último mes)

               REGLA: si la pregunta es amplia/exploratoria, invoca 3-5 tools.
               Si es específica (ej "top 10 trabajadores"), invoca solo 1.
               No abusar — máximo 5 tools por pregunta.

            3. Si el usuario pide algo que se puede visualizar (top X, evolución, distribución, comparativa),
               elige la herramienta apropiada — todas devuelven datos listos para gráficos.

            ## DESPUÉS DE INVOCAR HERRAMIENTAS

            - Resume el resultado en 2-3 frases máximo (no más, el usuario ve el gráfico)
            - Si encuentras algo destacable (gasto muy alto, anomalía urgente, valor atípico), MENCIÓNALO
            - Si hay varios gráficos, comenta brevemente qué muestra cada uno
            - Habla en español, profesional pero cercano
            - Usa euros con formato español: 52,92 €
            - Fechas en formato natural: "12 de marzo de 2026" o "hace 3 días"

            ## SIN HERRAMIENTAS

            Si la pregunta no requiere datos (saludos, ayuda, explicaciones generales sobre el sistema),
            responde directamente sin invocar herramientas.

            ## CONTEXTO

            Fecha actual: """ + LocalDate.now() + """

            Formato de fechas para tools: YYYY-MM-DD.

            ## RANGO DE DATOS DISPONIBLES (importante)

            La base de datos contiene **datos reales del Q1 2026** (1 enero a 31 marzo 2026):
            ~2.190 operaciones de Repsol, 883 tarjetas, 10 trabajadores.

            REGLA crítica: cuando el usuario NO especifique fechas explícitas:
            - Para "este mes", "el mes pasado", "últimos N meses" → usa **Q1 2026** (2026-01-01 a 2026-03-31).
              Es donde están los datos reales.
            - Para "este año" o "el último año" → usa **2026-01-01 a 2026-03-31**.
            - Para "siempre" o "todo el histórico" → usa **2025-01-01 a 2026-12-31**.

            Si el usuario SÍ especifica fechas concretas, respétalas tal cual.

            Esto evita devolver gráficos vacíos por elegir rangos sin datos.""";

        return Map.of("role", "system", "content", systemPrompt);
    }

    // ── Message conversion helpers ────────────────────────────────────────────

    private Map<String, Object> convertToOpenAiMessage(ChatMessage msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", msg.role());
        if (msg.content() != null) {
            m.put("content", msg.content());
        }
        if (msg.toolCallId() != null) {
            m.put("tool_call_id", msg.toolCallId());
        }
        if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            List<Map<String, Object>> tcs = msg.toolCalls().stream().map(tc -> {
                Map<String, Object> fn = Map.of("name", tc.toolName(), "arguments",
                    serializeArgs(tc.args()));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", tc.id());
                entry.put("type", "function");
                entry.put("function", fn);
                return entry;
            }).collect(Collectors.toList());
            m.put("tool_calls", tcs);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private ChatMessage convertFromOpenAiMessage(Map<String, Object> m) {
        if (m == null) return null;
        String role = (String) m.get("role");
        String content = m.get("content") instanceof String ? (String) m.get("content") : null;
        String toolCallId = (String) m.get("tool_call_id");

        List<ToolInvocation> toolCalls = null;
        Object tcs = m.get("tool_calls");
        if (tcs instanceof List<?> tcList && !tcList.isEmpty()) {
            toolCalls = tcList.stream()
                .filter(tc -> tc instanceof Map)
                .map(tc -> {
                    Map<String, Object> tcMap = (Map<String, Object>) tc;
                    String id = (String) tcMap.get("id");
                    Map<String, Object> fn = (Map<String, Object>) tcMap.get("function");
                    if (fn == null) return null;
                    String name = (String) fn.get("name");
                    String argsStr = (String) fn.get("arguments");
                    Map<String, Object> parsedArgs;
                    try {
                        parsedArgs = objectMapper.readValue(argsStr, new TypeReference<>() {});
                    } catch (Exception e) {
                        parsedArgs = Map.of();
                    }
                    return new ToolInvocation(id, name, parsedArgs, null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        return new ChatMessage(role, content, toolCallId, toolCalls);
    }

    private String serializeArgs(Map<String, Object> args) {
        try {
            return objectMapper.writeValueAsString(args != null ? args : Map.of());
        } catch (Exception e) {
            return "{}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
