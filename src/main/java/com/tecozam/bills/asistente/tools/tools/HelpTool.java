package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Devuelve un resumen estructurado de las capacidades del asistente.
 * Renderizado como tipo "list" con items por categoría.
 *
 * El LLM la invoca cuando el usuario pregunta cosas como:
 * - "¿Qué puedes hacer?"
 * - "ayuda" / "help"
 * - "¿Qué te puedo pedir?"
 * - "Muéstrame todo lo que puedes consultar"
 */
@Component
@Slf4j
public class HelpTool implements Tool {

    @Override
    public String getName() {
        return "get_help";
    }

    @Override
    public String getDescription() {
        return "Muestra las capacidades del asistente y ejemplos de preguntas. "
            + "Úsala cuando el usuario pregunte qué puedes hacer, qué le puedes mostrar, "
            + "cuáles son tus capacidades, o pida 'ayuda' / 'help' / 'comandos'.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        log.info("[Asistente] get_help invocada");

        // Items agrupados por categoría — el frontend los renderiza como list
        List<Map<String, Object>> items = new ArrayList<>();

        // ── Análisis y KPIs ────────────────────────────────────────────────
        items.add(itemSection("Análisis y KPIs"));
        items.add(item(
            "Resumen del estado actual",
            "info",
            "KPIs · alertas · operaciones · gasto total",
            "Dame un resumen del estado actual"
        ));
        items.add(item(
            "Panorama completo del negocio",
            "info",
            "5 gráficos en bento: KPIs + proveedores + conceptos + evolución + alertas",
            "Dame un panorama completo del negocio"
        ));

        // ── Gastos ────────────────────────────────────────────────────────
        items.add(itemSection("Gastos"));
        items.add(item(
            "Gasto por trabajador",
            "info",
            "Top N conductores con más gasto en un rango de fechas",
            "Top 10 trabajadores con más gasto este mes"
        ));
        items.add(item(
            "Gasto por proveedor",
            "info",
            "Repsol vs MOEVE vs otros, distribución porcentual",
            "Cuánto se gastó por proveedor en marzo"
        ));
        items.add(item(
            "Gasto por concepto",
            "info",
            "Diésel · gasolina · peajes · AdBlue · lavados",
            "Distribución del gasto por concepto del último año"
        ));
        items.add(item(
            "Gasto por centro de coste",
            "info",
            "España · Portugal · Staff · Ferrallas",
            "Reparto por centro de coste el último trimestre"
        ));
        items.add(item(
            "Evolución del gasto",
            "info",
            "Serie temporal con granularidad día / semana / mes",
            "Evolución del gasto los últimos 6 meses"
        ));
        items.add(item(
            "Comparar periodos",
            "info",
            "Diferencia de gasto entre 2 rangos de fechas",
            "Compara el gasto de febrero con marzo"
        ));

        // ── Operaciones y facturas ─────────────────────────────────────────
        items.add(itemSection("Operaciones y facturas"));
        items.add(item(
            "Operaciones recientes",
            "info",
            "Últimas N transacciones con todos los detalles",
            "Las últimas 20 operaciones"
        ));
        items.add(item(
            "Listar facturas",
            "info",
            "Facturas filtradas por estado de cotejo",
            "Facturas pendientes de cotejar"
        ));

        // ── Incidencias y anomalías ────────────────────────────────────────
        items.add(itemSection("Incidencias y anomalías"));
        items.add(item(
            "Alertas pendientes",
            "warning",
            "Vencimientos de préstamos · 3 días antes / 1 día / hoy / vencido",
            "Alertas pendientes urgentes"
        ));
        items.add(item(
            "Anomalías detectadas",
            "danger",
            "Descompensación · horario sospechoso · consumo excesivo",
            "Anomalías de descompensación esta semana"
        ));
        items.add(item(
            "Tickets con incidencia",
            "danger",
            "Tickets que no cuadran con la factura",
            "Tickets con incidencia abiertos"
        ));

        // ── Cómo escribir preguntas ────────────────────────────────────────
        items.add(itemSection("Tips para escribir preguntas"));
        items.add(item(
            "Usa lenguaje natural",
            "neutral",
            "No hace falta sintaxis especial — pregunta como hablarías con un humano",
            null
        ));
        items.add(item(
            "Pide varios gráficos a la vez",
            "neutral",
            "'Top 10 conductores y evolución del gasto' → 2 gráficos en paralelo",
            null
        ));
        items.add(item(
            "Especifica fechas si las tienes claras",
            "neutral",
            "'Marzo de 2026', 'últimos 6 meses', 'esta semana', 'desde el 1 de enero'",
            null
        ));
        items.add(item(
            "Fija gráficos importantes",
            "neutral",
            "Pulsa el icono 📌 en cada gráfico para conservarlo mientras exploras",
            null
        ));

        ChartSpec chartSpec = new ChartSpec(
            "list",
            "Capacidades del asistente",
            "Ejemplos de preguntas y comandos disponibles",
            items,
            Map.of("span", "full")
        );

        return new ToolResult(getName(), args, items, chartSpec);
    }

    private Map<String, Object> item(
        String label,
        String color,
        String description,
        String example
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("description", description);
        m.put("badge", "ejemplo");
        m.put("color", color);
        if (example != null) {
            m.put("value", example);
        }
        return m;
    }

    private Map<String, Object> itemSection(String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", "── " + label.toUpperCase() + " ──");
        m.put("description", "");
        m.put("color", "neutral");
        return m;
    }
}
