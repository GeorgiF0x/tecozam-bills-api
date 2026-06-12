package com.tecozam.bills.admin.application;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Endpoint admin para mover los VIATs Cepsa/Moeve que se importaron como tales
 * por el bug BILLS-04 al maestro Tarjeta, preservando sus IDs en una tabla de
 * auditoría.
 *
 * <p>Heurística de detección: VIAT con {@code descripcion} o {@code numero_serie}
 * que NO contiene ninguno de {@code PEAJE/AUTOPISTA/TUNEL/PORTAGEM} y cuyo número
 * empieza por {@code 7080} (prefijo erróneo del bug).
 *
 * <p>Idempotente: si tarjeta con número canónico ya existe, salta. Cada movimiento
 * deja registro en {@code auditoria_reclasificacion_viats}.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReclasificarViatsService {

    private static final String PREFIJO_TARJETA_MAL_CLASIFICADA = "7080";

    private final ViatRepository viatRepo;
    private final TarjetaRepository tarjetaRepo;
    private final ProveedorRepository proveedorRepo;
    private final EntityManager em;

    public ReclasificarViatsResponse reclasificar(boolean dryRun, String ejecutadoPor) {
        List<Viat> candidatos = viatRepo.findAll().stream()
                .filter(v -> v.getCodigo() != null && v.getCodigo().startsWith(PREFIJO_TARJETA_MAL_CLASIFICADA))
                .filter(this::noEsTelepeaje)
                .toList();

        List<String> numerosAfectados = candidatos.stream()
                .map(Viat::getCodigo)
                .limit(50)
                .toList();

        if (dryRun) {
            log.info("Reclasificar VIATs DRY-RUN: {} candidatos detectados", candidatos.size());
            return new ReclasificarViatsResponse(true, candidatos.size(), 0, numerosAfectados);
        }

        Proveedor cepsa = proveedorRepo.findByCodigo("MOEVE_CEPSA")
                .orElseThrow(() -> new BusinessException("Proveedor MOEVE_CEPSA no registrado en BD"));

        int movidos = 0;
        for (Viat viat : candidatos) {
            String numero = viat.getCodigo();
            // Idempotente: si ya existe la tarjeta, solo borrar el viat y auditar
            Tarjeta destino = tarjetaRepo.findByNumeroTarjeta(numero).orElseGet(() ->
                    tarjetaRepo.save(Tarjeta.builder()
                            .numeroTarjeta(numero)
                            .alias(viat.getNumeroSerie())
                            .proveedor(cepsa)
                            .estado(EstadoRecurso.DISPONIBLE)
                            .activa(true)
                            .build()));
            registrarAuditoria(viat.getId(), numero, destino.getId(), ejecutadoPor);
            viatRepo.delete(viat);
            movidos++;
        }
        log.info("Reclasificar VIATs ejecutado: {} movidos al maestro Tarjeta por {}", movidos, ejecutadoPor);
        return new ReclasificarViatsResponse(false, candidatos.size(), movidos, numerosAfectados);
    }

    private boolean noEsTelepeaje(Viat viat) {
        String descripcion = viat.getDescripcion() == null ? "" : viat.getDescripcion().toUpperCase();
        String numeroSerie = viat.getNumeroSerie() == null ? "" : viat.getNumeroSerie().toUpperCase();
        String haystack = descripcion + " " + numeroSerie;
        return !(haystack.contains("PEAJE")
                || haystack.contains("AUTOPISTA")
                || haystack.contains("TUNEL")
                || haystack.contains("PORTAGEM"));
    }

    private void registrarAuditoria(Long viatId, String numero, Long tarjetaId, String ejecutadoPor) {
        em.createNativeQuery(
                "INSERT INTO auditoria_reclasificacion_viats " +
                "(viat_id_origen, numero, tarjeta_id_dest, ejecutado_por, ejecutado_en) " +
                "VALUES (?1, ?2, ?3, ?4, ?5)")
                .setParameter(1, viatId)
                .setParameter(2, numero)
                .setParameter(3, tarjetaId)
                .setParameter(4, ejecutadoPor)
                .setParameter(5, Instant.now())
                .executeUpdate();
    }
}
