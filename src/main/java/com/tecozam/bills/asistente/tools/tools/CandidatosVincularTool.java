package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.ticket.application.TicketService;
import com.tecozam.bills.ticket.dto.TicketDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Herramienta de solo lectura: lista los tickets que aun no tienen una
 * operacion de tarjeta vinculada y requieren vinculacion manual, con un
 * deep-link al dialogo de vinculacion. No ejecuta ninguna accion.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CandidatosVincularTool implements Tool {

    private final TicketService ticketService;

    @Override
    public String getName() {
        return "get_tickets_pendientes_vincular";
    }

    @Override
    public String getDescription() {
        return "Devuelve los tickets que aún no tienen una operación de tarjeta vinculada y requieren "
            + "vinculación manual. Úsala cuando el usuario pregunte por tickets sin vincular, pendientes "
            + "de vincular, o quiera revisar vinculaciones manuales pendientes.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "limit", Map.of("type", "integer", "description", "Número máximo de tickets a devolver", "default", 20)
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;

        List<TicketDTO> candidatos = ticketService.candidatosParaVincular();

        List<Map<String, Object>> data = candidatos.stream()
            .limit(limit)
            .map(t -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("label", "#" + t.id() + " · " + t.estacion());
                item.put("description", t.importeTotal() + " € · " + t.fechaHora());
                item.put("badge", "pendiente");
                item.put("color", "warning");
                item.put("action", Map.of(
                    "label", "Abrir para vincular",
                    "href", "/tickets?vincularTicketId=" + t.id()
                ));
                return item;
            })
            .collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "list",
            "Tickets pendientes de vincular",
            data.size() + " ticket(s) pendientes de vinculación manual",
            data,
            Map.of("span", "half")
        );

        return new ToolResult(getName(), args, data, chart);
    }
}
