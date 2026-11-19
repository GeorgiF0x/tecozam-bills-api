package com.tecozam.bills.vehiculo.infrastructure.persistence;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, Long> {

    List<Vehiculo> findByActivoTrue();

    List<Vehiculo> findByEstadoAndActivoTrue(EstadoRecurso estado);

    List<Vehiculo> findByEstadoAndActivoTrueAndEliminadoEnIsNull(EstadoRecurso estado);

    Optional<Vehiculo> findByMatricula(String matricula);

    /**
     * Excluye los vehiculos borrados logicamente: uno rechazado (p.ej. un
     * "vehiculo de sustitucion" autorregistrado que el gestor rechazo) no
     * debe bloquear para siempre su matricula en altas o imports futuros.
     */
    boolean existsByMatriculaAndEliminadoEnIsNull(String matricula);

    boolean existsByCodigoObraAndEliminadoEnIsNull(String codigoObra);

    List<Vehiculo> findByEliminadoEnIsNull();

    List<Vehiculo> findByActivoTrueAndEliminadoEnIsNull();

    List<Vehiculo> findByEliminadoEnIsNotNull();

    List<Vehiculo> findByCategoriaAndEliminadoEnIsNull(CategoriaRecurso categoria);

    List<Vehiculo> findByCategoriaAndActivoTrueAndEliminadoEnIsNull(CategoriaRecurso categoria);
}
