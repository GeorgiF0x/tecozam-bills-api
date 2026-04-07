package com.tecozam.bills.auth.infrastructure.persistence;

import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.shared.domain.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsernameAndActivoTrue(String username);

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRolAndActivoTrue(Rol rol);
}
