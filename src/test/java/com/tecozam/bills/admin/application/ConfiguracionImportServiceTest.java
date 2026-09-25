package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.domain.ConfiguracionImport;
import com.tecozam.bills.admin.dto.ConfiguracionImportDTO;
import com.tecozam.bills.admin.infrastructure.persistence.ConfiguracionImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracionImportServiceTest {

    @Mock ConfiguracionImportRepository configuracionImportRepository;

    ConfiguracionImportService service;

    ConfiguracionImport configuracion;

    @BeforeEach
    void setUp() {
        service = new ConfiguracionImportService(configuracionImportRepository);

        configuracion = ConfiguracionImport.builder()
                .modoLlmActivo(false)
                .build();
        configuracion.setId(1L);
    }

    @Test
    @DisplayName("obtener devuelve el estado actual de la fila unica existente")
    void obtener_devuelveEstadoActual() {
        when(configuracionImportRepository.findAll()).thenReturn(List.of(configuracion));

        ConfiguracionImportDTO dto = service.obtener();

        assertThat(dto.modoLlmActivo()).isFalse();
    }

    @Test
    @DisplayName("actualizar activa el modo LLM y registra quien y cuando lo cambio")
    void actualizar_activaModoLlmYRegistraAuditoria() {
        when(configuracionImportRepository.findAll()).thenReturn(List.of(configuracion));
        when(configuracionImportRepository.save(any(ConfiguracionImport.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConfiguracionImportDTO dto = service.actualizar(true, "admin1");

        ArgumentCaptor<ConfiguracionImport> captor = ArgumentCaptor.forClass(ConfiguracionImport.class);
        verify(configuracionImportRepository).save(captor.capture());

        ConfiguracionImport guardada = captor.getValue();
        assertThat(guardada.isModoLlmActivo()).isTrue();
        assertThat(guardada.getActualizadoPor()).isEqualTo("admin1");
        assertThat(guardada.getActualizadoEn()).isNotNull();

        assertThat(dto.modoLlmActivo()).isTrue();
        assertThat(dto.actualizadoPor()).isEqualTo("admin1");
    }
}
