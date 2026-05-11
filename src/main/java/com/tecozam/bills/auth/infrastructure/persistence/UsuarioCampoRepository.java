package com.tecozam.bills.auth.infrastructure.persistence;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioCampoRepository extends JpaRepository<UsuarioCampo, Long> {

    Optional<UsuarioCampo> findByUsername(String username);

    Optional<UsuarioCampo> findByUsernameAndActivoTrue(String username);

    boolean existsByUsername(String username);

    List<UsuarioCampo> findByEstadoRegistro(EstadoRegistro estadoRegistro);
}
