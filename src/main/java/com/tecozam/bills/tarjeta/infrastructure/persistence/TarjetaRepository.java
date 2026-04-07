package com.tecozam.bills.tarjeta.infrastructure.persistence;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TarjetaRepository extends JpaRepository<Tarjeta, Long> {

    List<Tarjeta> findByActivaTrue();

    List<Tarjeta> findByEstadoAndActivaTrue(EstadoRecurso estado);

    Optional<Tarjeta> findByNumeroTarjeta(String numeroTarjeta);

    boolean existsByNumeroTarjeta(String numeroTarjeta);
}
