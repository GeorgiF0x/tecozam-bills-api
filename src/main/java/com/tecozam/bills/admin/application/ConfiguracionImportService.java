package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.domain.ConfiguracionImport;
import com.tecozam.bills.admin.dto.ConfiguracionImportDTO;
import com.tecozam.bills.admin.infrastructure.persistence.ConfiguracionImportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Interruptor global (fila unica) que decide si el import de listado de
 * tarjetas y de facturas/extracto usa el parser determinista de siempre
 * (por defecto) o un LLM. Cambiar este valor tiene impacto financiero real
 * (ver odd/tasks/import-llm-switch.md), por eso cada cambio queda en el log.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ConfiguracionImportService {

    private final ConfiguracionImportRepository configuracionImportRepository;

    @Transactional(readOnly = true)
    public ConfiguracionImportDTO obtener() {
        return toDTO(obtenerEntidad());
    }

    public ConfiguracionImportDTO actualizar(boolean modoLlmActivo, String usuario) {
        ConfiguracionImport configuracion = obtenerEntidad();

        boolean cambioDeEstado = configuracion.isModoLlmActivo() != modoLlmActivo;

        configuracion.setModoLlmActivo(modoLlmActivo);
        configuracion.setActualizadoEn(LocalDateTime.now());
        configuracion.setActualizadoPor(usuario);

        configuracion = configuracionImportRepository.save(configuracion);

        if (cambioDeEstado) {
            log.info("Modo LLM de import {} por {}", modoLlmActivo ? "ACTIVADO" : "DESACTIVADO", usuario);
        }

        return toDTO(configuracion);
    }

    /**
     * Lee la fila unica de configuracion. La migracion V26 ya la crea con
     * modoLlmActivo=false, pero por robustez (p.ej. entornos de test que no
     * corren Flyway) se crea perezosamente si no existe todavia.
     */
    private ConfiguracionImport obtenerEntidad() {
        List<ConfiguracionImport> filas = configuracionImportRepository.findAll();
        if (!filas.isEmpty()) {
            return filas.get(0);
        }
        return configuracionImportRepository.save(
                ConfiguracionImport.builder().modoLlmActivo(false).build());
    }

    private ConfiguracionImportDTO toDTO(ConfiguracionImport configuracion) {
        return new ConfiguracionImportDTO(
                configuracion.isModoLlmActivo(),
                configuracion.getActualizadoEn(),
                configuracion.getActualizadoPor());
    }
}
