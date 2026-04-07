package com.tecozam.bills.dashboard.application;

import com.tecozam.bills.alerta.infrastructure.persistence.AlertaPrestamoRepository;
import com.tecozam.bills.dashboard.dto.DashboardStatsDTO;
import com.tecozam.bills.dashboard.dto.EvolucionMensualDTO;
import com.tecozam.bills.dashboard.dto.GastoPorProveedorDTO;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardService {

    private final FacturaRepository facturaRepository;
    private final PrestamoRepository prestamoRepository;
    private final AlertaPrestamoRepository alertaPrestamoRepository;
    private final TicketRepository ticketRepository;
    private final OperacionRepository operacionRepository;

    public DashboardStatsDTO getStats() {
        long totalFacturas = facturaRepository.count();
        long totalOperaciones = operacionRepository.count();

        BigDecimal importeTotal = Optional.ofNullable(facturaRepository.sumTotalFactura())
                .orElse(BigDecimal.ZERO);

        long prestamosActivos = prestamoRepository.countByEstado("ACTIVO");
        long alertasPendientes = alertaPrestamoRepository.countByLeidaFalse();

        long anomaliasDetectadas = ticketRepository.countByEstadoCotejoIn(
                List.of("INCIDENCIA", "SIN_COINCIDENCIA"));

        long totalTickets = ticketRepository.count();
        long ticketsCotejados = ticketRepository.findByEstadoCotejo("COTEJADO").size();
        double porcentajeCotejado = totalTickets > 0
                ? (ticketsCotejados * 100.0) / totalTickets
                : 0.0;

        List<GastoPorProveedorDTO> gastoPorProveedor;
        try {
            gastoPorProveedor = facturaRepository.findGastoPorProveedor();
        } catch (Exception e) {
            log.warn("Error obteniendo gasto por proveedor: {}", e.getMessage());
            gastoPorProveedor = List.of();
        }

        List<EvolucionMensualDTO> evolucionMensual;
        try {
            LocalDate desde = LocalDate.now().minusMonths(12).withDayOfMonth(1);
            evolucionMensual = facturaRepository.findEvolucionMensual(desde);
        } catch (Exception e) {
            log.warn("Error obteniendo evolución mensual: {}", e.getMessage());
            evolucionMensual = List.of();
        }

        return new DashboardStatsDTO(
                totalFacturas,
                totalOperaciones,
                importeTotal,
                porcentajeCotejado,
                prestamosActivos,
                alertasPendientes,
                anomaliasDetectadas,
                gastoPorProveedor,
                evolucionMensual
        );
    }
}
