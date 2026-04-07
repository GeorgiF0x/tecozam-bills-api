package com.tecozam.bills.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsDTO(
        long totalFacturas,
        long totalOperaciones,
        BigDecimal importeTotal,
        double porcentajeCotejado,
        long prestamosActivos,
        long alertasPendientes,
        long anomaliasDetectadas,
        List<GastoPorProveedorDTO> gastoPorProveedor,
        List<EvolucionMensualDTO> evolucionMensual
) {}
