package com.tecozam.bills.trabajador.infrastructure.persistence;

import com.tecozam.bills.trabajador.domain.Trabajador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrabajadorRepository extends JpaRepository<Trabajador, Long> {

    List<Trabajador> findByActivoTrue();

    Optional<Trabajador> findByEmail(String email);

    boolean existsByEmail(String email);
}
