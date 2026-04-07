package com.tecozam.bills.factura.infrastructure.persistence;

import com.tecozam.bills.dashboard.dto.EvolucionMensualDTO;
import com.tecozam.bills.dashboard.dto.GastoPorProveedorDTO;
import com.tecozam.bills.factura.domain.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    Optional<Factura> findByNumFacturaAndProveedorId(String numFactura, Long proveedorId);

    boolean existsByNumFacturaAndProveedorId(String numFactura, Long proveedorId);

    List<Factura> findByProveedorIdOrderByFechaDesc(Long proveedorId);

    @Query("SELECT COALESCE(SUM(f.totalFactura), 0) FROM Factura f")
    BigDecimal sumTotalFactura();

    @Query("SELECT new com.tecozam.bills.dashboard.dto.GastoPorProveedorDTO(f.proveedor.nombre, SUM(f.totalFactura), COUNT(f)) FROM Factura f GROUP BY f.proveedor.nombre ORDER BY SUM(f.totalFactura) DESC")
    List<GastoPorProveedorDTO> findGastoPorProveedor();

    @Query("SELECT new com.tecozam.bills.dashboard.dto.EvolucionMensualDTO(CONCAT(CAST(YEAR(f.fecha) AS string), '-', LPAD(CAST(MONTH(f.fecha) AS string), 2, '0')), SUM(f.totalFactura), COUNT(f)) FROM Factura f WHERE f.fecha >= :desde GROUP BY YEAR(f.fecha), MONTH(f.fecha) ORDER BY YEAR(f.fecha), MONTH(f.fecha)")
    List<EvolucionMensualDTO> findEvolucionMensual(@Param("desde") LocalDate desde);
}
