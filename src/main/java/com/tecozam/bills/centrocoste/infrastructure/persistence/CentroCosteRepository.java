package com.tecozam.bills.centrocoste.infrastructure.persistence;

import com.tecozam.bills.centrocoste.domain.CentroCoste;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CentroCosteRepository extends JpaRepository<CentroCoste, Long> {

    List<CentroCoste> findByActivoTrue();

    Optional<CentroCoste> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
