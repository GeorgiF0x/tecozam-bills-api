package com.tecozam.bills.admin.application;

import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.factura.infrastructure.persistence.TarjetaResumenRepository;
import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
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
 * Reclasificación automática TARJETA→VIAT para Cepsa/Moeve, dirección inversa
 * de {@link ReclasificarViatsService} (mismo patrón: dry-run + tabla de
 * auditoría + idempotente). Ver decisión del usuario y alcance completos en
 * odd/tasks/bugs-fecha-nan-y-reclasificacion-cepsa.md.
 *
 * <p>Regla de negocio (NO es un prefijo de número, a diferencia de
 * {@link ReclasificarViatsService}): una tarjeta Cepsa/Moeve se reclasifica
 * cuando tiene <b>3 o más operaciones reales en BD</b> (historial completo,
 * no solo la factura recién importada) y <b>el 100% son de concepto
 * PEAJE</b> (0% combustible/otros), usando el campo ya existente
 * {@link com.tecozam.bills.factura.domain.Operacion#getConceptoUnificado()}
 * (enum {@link ConceptoUnificado}) que el parser ya asigna por operación —
 * no se reimplementa un segundo clasificador de texto.
 *
 * <p>Punto de enganche: se invoca automáticamente desde
 * {@code FacturaService.importar(...)} (pipeline de FACTURAS, no el de
 * listado maestro {@code ListadoTarjetasImportService}) justo después de
 * vincular las tarjetas del maestro a los {@code TarjetaResumen} de la
 * factura recién importada, limitado a las tarjetas tocadas en esa factura.
 *
 * <p><b>Riesgo de integridad investigado y mitigado:</b> a diferencia de
 * {@code Operacion} (que solo referencia {@code TarjetaResumen}, nunca la
 * tarjeta del maestro directamente), tanto {@code Ticket.tarjeta} como
 * {@code TarjetaAsignacion.tarjeta} son FK directas a {@code Tarjeta}. Borrar
 * una tarjeta con tickets o asignaciones reales rompería esas referencias
 * (un ticket ya cotejado perdería su tarjeta). Por eso esta reclasificación
 * SOLO mueve tarjetas sin tickets ni asignaciones vinculados
 * ({@link TicketRepository#existsByTarjetaId} /
 * {@link TarjetaAsignacionRepository#existsByTarjetaId}); si existen, la
 * candidata se deja protegida (reportada, nunca movida en silencio).
 * {@code TarjetaResumen.tarjeta} sí es un FK opcional a la tarjeta del
 * maestro, pero el cotejo lo ignora (usa {@code numTarjeta} como String —
 * ver {@code OperacionRepository.findParaCotejo*}), así que antes de borrar
 * la tarjeta se desvinculan (se ponen a {@code null}) todos los
 * {@code TarjetaResumen} que la referencian, de cualquier factura pasada o
 * presente.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReclasificarTarjetasAViatService {

    private static final int UMBRAL_MINIMO_OPERACIONES = 3;

    private final OperacionRepository operacionRepository;
    private final TarjetaRepository tarjetaRepository;
    private final TarjetaResumenRepository tarjetaResumenRepository;
    private final TarjetaAsignacionRepository tarjetaAsignacionRepository;
    private final TicketRepository ticketRepository;
    private final ViatRepository viatRepository;
    private final EntityManager em;

    /**
     * Evalúa y, si no es dry-run, reclasifica las tarjetas indicadas (se
     * espera que sean las tarjetas del maestro tocadas por una factura
     * Cepsa/Moeve recién importada; se deduplican por id).
     */
    public ReclasificarTarjetasAViatResponse reclasificarTarjetasTocadas(List<Tarjeta> tarjetasTocadas,
            boolean dryRun, String ejecutadoPor) {
        List<Tarjeta> distintas = tarjetasTocadas.stream()
                .filter(t -> t != null && t.getId() != null)
                .distinct()
                .toList();

        int candidatos = 0;
        int movidas = 0;
        List<String> numerosMovidos = new ArrayList<>();
        List<String> numerosProtegidos = new ArrayList<>();

        for (Tarjeta tarjeta : distintas) {
            long totalOperaciones = operacionRepository.countByTarjetaResumen_Tarjeta_Id(tarjeta.getId());
            if (totalOperaciones < UMBRAL_MINIMO_OPERACIONES) {
                continue;
            }
            long noPeaje = operacionRepository.countByTarjetaResumen_Tarjeta_IdAndConceptoUnificadoNot(
                    tarjeta.getId(), ConceptoUnificado.PEAJE.name());
            if (noPeaje > 0) {
                continue;
            }

            candidatos++;

            boolean tieneTickets = ticketRepository.existsByTarjetaId(tarjeta.getId());
            boolean tieneAsignaciones = tarjetaAsignacionRepository.existsByTarjetaId(tarjeta.getId());
            if (tieneTickets || tieneAsignaciones) {
                log.warn("[ReclasificarTarjetasAViat] Tarjeta {} (id={}) cumple el umbral de peaje "
                                + "({} operaciones, 100% peaje) pero NO se reclasifica: tiene {}{}{} vinculados "
                                + "por FK directa — requiere revisión manual",
                        tarjeta.getNumeroTarjeta(), tarjeta.getId(), totalOperaciones,
                        tieneTickets ? "tickets" : "", tieneTickets && tieneAsignaciones ? " y " : "",
                        tieneAsignaciones ? "asignaciones" : "");
                numerosProtegidos.add(tarjeta.getNumeroTarjeta());
                continue;
            }

            if (dryRun) {
                log.info("[ReclasificarTarjetasAViat] DRY-RUN: tarjeta {} (id={}) candidata a VIAT "
                                + "({} operaciones, 100% peaje)",
                        tarjeta.getNumeroTarjeta(), tarjeta.getId(), totalOperaciones);
                numerosMovidos.add(tarjeta.getNumeroTarjeta());
                continue;
            }

            moverTarjetaAViat(tarjeta, totalOperaciones, ejecutadoPor);
            numerosMovidos.add(tarjeta.getNumeroTarjeta());
            movidas++;
        }

        if (candidatos > 0) {
            log.info("[ReclasificarTarjetasAViat] {} candidatas detectadas, {} movidas, {} protegidas (dryRun={})",
                    candidatos, movidas, numerosProtegidos.size(), dryRun);
        }

        return new ReclasificarTarjetasAViatResponse(dryRun, candidatos, movidas, numerosProtegidos.size(),
                numerosMovidos, numerosProtegidos);
    }

    private void moverTarjetaAViat(Tarjeta tarjeta, long totalOperaciones, String ejecutadoPor) {
        String numero = tarjeta.getNumeroTarjeta();

        // Idempotente: si ya existe un Viat con este código (p. ej. porque la
        // misma tarjeta física ya se reclasificó en un import anterior y el
        // maestro volvió a crear una Tarjeta nueva con el mismo número en
        // este import), se reutiliza en vez de duplicar.
        Viat viat = viatRepository.findByCodigo(numero).orElseGet(() -> viatRepository.save(Viat.builder()
                .codigo(numero)
                .numeroSerie(tarjeta.getAlias())
                .estado(tarjeta.getEstado() != null ? tarjeta.getEstado() : EstadoRecurso.DISPONIBLE)
                .activo(tarjeta.isActiva())
                .build()));

        // Desvincular TarjetaResumen.tarjeta (FK opcional, de cualquier
        // factura pasada o presente) antes de borrar: el cotejo no depende de
        // este FK (usa numTarjeta como String), así que desvincular es seguro.
        List<TarjetaResumen> vinculadas = tarjetaResumenRepository.findByTarjetaId(tarjeta.getId());
        vinculadas.forEach(tr -> tr.setTarjeta(null));
        tarjetaResumenRepository.saveAll(vinculadas);

        long operacionesPeaje = totalOperaciones; // 100% peaje, ya verificado por el llamador
        registrarAuditoria(tarjeta.getId(), numero, viat.getId(), totalOperaciones, operacionesPeaje, ejecutadoPor);

        tarjetaRepository.delete(tarjeta);
        log.info("[ReclasificarTarjetasAViat] Tarjeta {} (id={}) movida a VIAT (id={}): {} operaciones, 100% peaje",
                numero, tarjeta.getId(), viat.getId(), totalOperaciones);
    }

    private void registrarAuditoria(Long tarjetaId, String numero, Long viatId, long totalOperaciones,
            long operacionesPeaje, String ejecutadoPor) {
        em.createNativeQuery(
                "INSERT INTO auditoria_reclasificacion_tarjetas " +
                "(tarjeta_id_origen, numero, viat_id_dest, total_operaciones, operaciones_peaje, " +
                "ejecutado_por, ejecutado_en) " +
                "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)")
                .setParameter(1, tarjetaId)
                .setParameter(2, numero)
                .setParameter(3, viatId)
                .setParameter(4, (int) totalOperaciones)
                .setParameter(5, (int) operacionesPeaje)
                .setParameter(6, ejecutadoPor)
                .setParameter(7, Instant.now())
                .executeUpdate();
    }
}
