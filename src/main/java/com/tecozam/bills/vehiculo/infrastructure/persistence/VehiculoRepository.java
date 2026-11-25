package com.tecozam.bills.vehiculo.infrastructure.persistence;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Vehiculos no borrados cuya matricula, en mayusculas y sin espacios ni
     * guiones, es {@code matriculaNormalizada} (que ya debe venir asi): evita
     * duplicados como "1234ABC" y "1234-ABC" en el alta de sustitucion. Las
     * matriculas se siguen guardando tal cual se escribieron.
     */
    @Query("SELECT v FROM Vehiculo v WHERE v.eliminadoEn IS NULL " +
            "AND UPPER(REPLACE(REPLACE(v.matricula, ' ', ''), '-', '')) = :matriculaNormalizada")
    List<Vehiculo> findActivosPorMatriculaNormalizada(@Param("matriculaNormalizada") String matriculaNormalizada);

    boolean existsByCodigoObraAndEliminadoEnIsNull(String codigoObra);

    List<Vehiculo> findByEliminadoEnIsNull();

    List<Vehiculo> findByActivoTrueAndEliminadoEnIsNull();

    List<Vehiculo> findByEliminadoEnIsNotNull();

    List<Vehiculo> findByCategoriaAndEliminadoEnIsNull(CategoriaRecurso categoria);

    List<Vehiculo> findByCategoriaAndActivoTrueAndEliminadoEnIsNull(CategoriaRecurso categoria);
}
