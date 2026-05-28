package com.tecozam.bills.auth.infrastructure.persistence;

import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioOficinaRepository extends JpaRepository<UsuarioOficina, Long> {

    Optional<UsuarioOficina> findByUsername(String username);

    Optional<UsuarioOficina> findByUsernameAndActivoTrue(String username);

    boolean existsByUsername(String username);

    List<UsuarioOficina> findByEstadoRegistro(EstadoRegistro estadoRegistro);

    long countByRolAndActivoTrue(Rol rol);
}
