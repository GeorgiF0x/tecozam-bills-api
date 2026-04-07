package com.tecozam.bills.factura.infrastructure.persistence;

import com.tecozam.bills.factura.domain.Operacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OperacionRepository extends JpaRepository<Operacion, Long> {

    @Query("SELECT o FROM Operacion o " +
            "LEFT JOIN FETCH o.tarjetaResumen tr " +
            "LEFT JOIN FETCH tr.factura f " +
            "LEFT JOIN FETCH f.proveedor " +
            "WHERE o.factura.id = :facturaId")
    List<Operacion> findByFacturaId(@Param("facturaId") Long facturaId);

    @Query("SELECT o FROM Operacion o " +
            "WHERE o.fechaHora BETWEEN :desde AND :hasta " +
            "AND o.importeTotal = :importe " +
            "AND o.tarjetaResumen.numTarjeta LIKE %:ultimos4")
    List<Operacion> findParaCotejoConTarjeta(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("importe") BigDecimal importe,
            @Param("ultimos4") String ultimos4
    );

    @Query("SELECT o FROM Operacion o WHERE o.fechaHora BETWEEN :desde AND :hasta AND ABS(o.importeTotal - :importe) < 0.10")
    List<Operacion> findParaCotejo(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("importe") BigDecimal importe
    );

    @Query("SELECT o FROM Operacion o " +
            "JOIN o.tarjetaResumen tr " +
            "JOIN tr.factura f " +
            "WHERE (:proveedorId IS NULL OR f.proveedor.id = :proveedorId) " +
            "AND (:conceptoUnificado IS NULL OR o.conceptoUnificado = :conceptoUnificado) " +
            "AND (:fechaDesde IS NULL OR o.fechaHora >= :fechaDesde) " +
            "AND (:fechaHasta IS NULL OR o.fechaHora <= :fechaHasta) " +
            "ORDER BY o.fechaHora DESC")
    Page<Operacion> findWithFilters(
            @Param("proveedorId") Long proveedorId,
            @Param("conceptoUnificado") String conceptoUnificado,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable
    );

    @Query("SELECT o FROM Operacion o " +
            "LEFT JOIN FETCH o.tarjetaResumen tr " +
            "LEFT JOIN FETCH tr.factura f " +
            "LEFT JOIN FETCH f.proveedor " +
            "WHERE (:proveedorId IS NULL OR f.proveedor.id = :proveedorId) " +
            "AND (:conceptoUnificado IS NULL OR o.conceptoUnificado = :conceptoUnificado) " +
            "AND (:fechaDesde IS NULL OR o.fechaHora >= :fechaDesde) " +
            "AND (:fechaHasta IS NULL OR o.fechaHora <= :fechaHasta) " +
            "ORDER BY o.fechaHora DESC")
    Page<Operacion> findWithFiltersEager(
            @Param("proveedorId") Long proveedorId,
            @Param("conceptoUnificado") String conceptoUnificado,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable
    );
}
