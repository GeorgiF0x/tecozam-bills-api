package com.tecozam.bills.tarjeta.infrastructure.persistence;

import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TarjetaAsignacionRepository extends JpaRepository<TarjetaAsignacion, Long> {

    List<TarjetaAsignacion> findByTarjetaIdOrderByFechaDesdeDesc(Long tarjetaId);

    Optional<TarjetaAsignacion> findByTarjetaIdAndFechaHastaIsNull(Long tarjetaId);

    List<TarjetaAsignacion> findByTrabajadorIdAndFechaHastaIsNull(Long trabajadorId);

    Optional<TarjetaAsignacion> findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(Long tarjetaId, Long trabajadorId);
}
