package com.tecozam.bills.tarjeta.infrastructure.persistence;

import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TarjetaAsignacionRepository extends JpaRepository<TarjetaAsignacion, Long> {

    List<TarjetaAsignacion> findByTarjetaIdOrderByFechaDesdeDesc(Long tarjetaId);

    /**
     * Una asignacion esta activa si no tiene fecha de fin, o si esa fecha
     * todavia no ha pasado. Antes se exigia fechaHasta IS NULL, lo que dejaba
     * fuera a las asignaciones con fecha de fin futura desde el instante en
     * que se creaban.
     */
    @Query("SELECT a FROM TarjetaAsignacion a WHERE a.tarjeta.id = :tarjetaId "
            + "AND (a.fechaHasta IS NULL OR a.fechaHasta >= :hoy)")
    Optional<TarjetaAsignacion> findActivaByTarjetaId(@Param("tarjetaId") Long tarjetaId, @Param("hoy") LocalDate hoy);

    @Query("SELECT a FROM TarjetaAsignacion a WHERE a.trabajador.id = :trabajadorId "
            + "AND (a.fechaHasta IS NULL OR a.fechaHasta >= :hoy)")
    List<TarjetaAsignacion> findActivasByTrabajadorId(@Param("trabajadorId") Long trabajadorId, @Param("hoy") LocalDate hoy);

    @Query("SELECT a FROM TarjetaAsignacion a WHERE a.tarjeta.id = :tarjetaId AND a.trabajador.id = :trabajadorId "
            + "AND (a.fechaHasta IS NULL OR a.fechaHasta >= :hoy)")
    Optional<TarjetaAsignacion> findActivaByTarjetaIdAndTrabajadorId(
            @Param("tarjetaId") Long tarjetaId, @Param("trabajadorId") Long trabajadorId, @Param("hoy") LocalDate hoy);
}
