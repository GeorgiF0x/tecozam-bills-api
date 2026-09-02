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

    /** Devuelve el rango de fechas disponible: [min, max] o null si no hay datos */
    @Query("SELECT MIN(o.fechaHora), MAX(o.fechaHora) FROM Operacion o WHERE o.fechaHora IS NOT NULL")
    Object[] findDateRange();

    @Query("SELECT o FROM Operacion o " +
            "LEFT JOIN FETCH o.tarjetaResumen tr " +
            "LEFT JOIN FETCH tr.factura f " +
            "LEFT JOIN FETCH f.proveedor " +
            "WHERE o.factura.id = :facturaId")
    List<Operacion> findByFacturaId(@Param("facturaId") Long facturaId);

    /**
     * Candidatas para cotejo cuando el ticket sabe la tarjeta: tarjeta+fecha/hora
     * es una clave casi unica, así que el importe NO filtra aqui (las tarjetas de
     * flota se facturan a precio pactado, distinto del precio de venta al publico
     * que marca el ticket fisico — filtrar por importe exacto descartaba
     * candidatas validas). El importe se usa despues, en detectarDiscrepancia y en
     * el desempate entre varias candidatas.
     */
    @Query("SELECT o FROM Operacion o " +
            "WHERE o.fechaHora BETWEEN :desde AND :hasta " +
            "AND o.tarjetaResumen.numTarjeta LIKE %:ultimos4")
    List<Operacion> findParaCotejoConTarjeta(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
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

    /**
     * @param numTarjeta filtra por ultimos digitos de la tarjeta (opcional). Sin
     *                    esto, el buscador de "vincular operacion" solo veia los
     *                    200 resultados mas recientes del rango de fechas de
     *                    TODAS las tarjetas de la factura — con flotas grandes,
     *                    la operacion real quedaba fuera de esos 200 aunque
     *                    existiera (bug real reportado por el cliente).
     * @param q          busca por establecimiento o concepto original (opcional),
     *                    en servidor en vez de solo sobre la pagina ya traida.
     */
    @Query("SELECT o FROM Operacion o " +
            "LEFT JOIN FETCH o.tarjetaResumen tr " +
            "LEFT JOIN FETCH tr.factura f " +
            "LEFT JOIN FETCH f.proveedor " +
            "WHERE (:proveedorId IS NULL OR f.proveedor.id = :proveedorId) " +
            "AND (:conceptoUnificado IS NULL OR o.conceptoUnificado = :conceptoUnificado) " +
            "AND (:fechaDesde IS NULL OR o.fechaHora >= :fechaDesde) " +
            "AND (:fechaHasta IS NULL OR o.fechaHora <= :fechaHasta) " +
            "AND (:numTarjeta IS NULL OR tr.numTarjeta LIKE %:numTarjeta) " +
            "AND (:q IS NULL OR LOWER(o.establecimiento) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "     OR LOWER(o.conceptoOriginal) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY o.fechaHora DESC")
    Page<Operacion> findWithFiltersEager(
            @Param("proveedorId") Long proveedorId,
            @Param("conceptoUnificado") String conceptoUnificado,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            @Param("numTarjeta") String numTarjeta,
            @Param("q") String q,
            Pageable pageable
    );
}
