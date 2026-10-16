package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.ticket.application.TicketService;
import com.tecozam.bills.ticket.dto.TicketDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidatosVincularToolTest {

    @Mock TicketService ticketService;

    CandidatosVincularTool tool;

    @BeforeEach
    void setUp() {
        tool = new CandidatosVincularTool(ticketService);
    }

    @Test
    @DisplayName("execute devuelve un item por cada TicketDTO candidato, con href e importe correctos, chartSpec tipo list")
    void execute_devuelveItemsConHrefYChartSpecList() {
        TicketDTO ticket1 = ticketDto(101L, "Estacion Repsol Norte", new BigDecimal("45.30"),
                LocalDateTime.of(2026, 3, 1, 10, 0));
        TicketDTO ticket2 = ticketDto(202L, "Estacion Moeve Sur", new BigDecimal("12.00"),
                LocalDateTime.of(2026, 3, 2, 11, 30));

        when(ticketService.candidatosParaVincular()).thenReturn(List.of(ticket1, ticket2));

        ToolResult result = tool.execute(Map.of());

        assertThat(result.toolName()).isEqualTo("get_tickets_pendientes_vincular");
        assertThat(result.chartSpec().type()).isEqualTo("list");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.data();
        assertThat(items).hasSize(2);

        Map<String, Object> item1 = items.get(0);
        assertThat(item1.get("label")).isEqualTo("#101 · Estacion Repsol Norte");
        assertThat(item1.get("badge")).isEqualTo("pendiente");
        assertThat(item1.get("color")).isEqualTo("warning");

        @SuppressWarnings("unchecked")
        Map<String, Object> action1 = (Map<String, Object>) item1.get("action");
        assertThat(action1.get("href")).isEqualTo("/tickets?vincularTicketId=101");

        Map<String, Object> item2 = items.get(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> action2 = (Map<String, Object>) item2.get("action");
        assertThat(action2.get("href")).isEqualTo("/tickets?vincularTicketId=202");
    }

    @Test
    @DisplayName("execute respeta el limit opcional")
    void execute_respetaLimit() {
        TicketDTO ticket1 = ticketDto(1L, "A", BigDecimal.TEN, LocalDateTime.now());
        TicketDTO ticket2 = ticketDto(2L, "B", BigDecimal.TEN, LocalDateTime.now());
        when(ticketService.candidatosParaVincular()).thenReturn(List.of(ticket1, ticket2));

        ToolResult result = tool.execute(Map.of("limit", 1));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.data();
        assertThat(items).hasSize(1);
    }

    private static TicketDTO ticketDto(Long id, String estacion, BigDecimal importeTotal, LocalDateTime fechaHora) {
        return new TicketDTO(
                id, null, null, null, null, null, null, null,
                estacion, null, fechaHora, null, null, null, null,
                null, null, importeTotal, null, null, null, null,
                null, "PENDIENTE", null, null, null, null, null,
                null, null, false, null
        );
    }
}
