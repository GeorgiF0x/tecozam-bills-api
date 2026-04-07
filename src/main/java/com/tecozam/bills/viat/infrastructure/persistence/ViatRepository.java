package com.tecozam.bills.viat.infrastructure.persistence;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.viat.domain.Viat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ViatRepository extends JpaRepository<Viat, Long> {

    List<Viat> findByActivoTrue();

    List<Viat> findByEstadoAndActivoTrue(EstadoRecurso estado);

    Optional<Viat> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
