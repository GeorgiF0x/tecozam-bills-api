package com.tecozam.bills.factura.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecozam.bills.factura.application.FacturaLoteImportService;
import com.tecozam.bills.factura.application.FacturaService;
import com.tecozam.bills.factura.dto.FacturaDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaControllerTest {

    @Mock FacturaService facturaService;
    @Mock FacturaLoteImportService facturaLoteImportService;

    @Test
    @DisplayName("PATCH /{id}/revisada guarda el flag primero y despues lanza el cotejo en una llamada aparte (odd/tasks/paquete-cotejo-b.md, S3)")
    void marcarRevisada_guardaFlagYDespuesRecoteja() {
        FacturaController controller = new FacturaController(facturaService, facturaLoteImportService, new ObjectMapper());
        FacturaDTO dto = new FacturaDTO(10L, null, null, "FA-001", null, null, null, null, null, null,
                null, null, null, null, null, null, null, 0, 0, List.of(), false);
        when(facturaService.marcarRevisada(10L)).thenReturn(dto);

        assertThat(controller.marcarRevisada(10L)).isEqualTo(dto);

        InOrder orden = inOrder(facturaService);
        orden.verify(facturaService).marcarRevisada(10L);
        orden.verify(facturaService).recotejarPendientes();
    }
}
