package com.tecozam.bills.vehiculo.infrastructure.persistence;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    List<Vehiculo> findByActivoTrue();

    List<Vehiculo> findByEstadoAndActivoTrue(EstadoRecurso estado);

    Optional<Vehiculo> findByMatricula(String matricula);

    boolean existsByMatricula(String matricula);
}
