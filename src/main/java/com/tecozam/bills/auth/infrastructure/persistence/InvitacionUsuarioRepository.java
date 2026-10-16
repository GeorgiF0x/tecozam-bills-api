package com.tecozam.bills.auth.infrastructure.persistence;

import com.tecozam.bills.auth.domain.InvitacionUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvitacionUsuarioRepository extends JpaRepository<InvitacionUsuario, Long> {

    Optional<InvitacionUsuario> findByTokenHash(String tokenHash);

    List<InvitacionUsuario> findByUsuarioIdAndUsedAtIsNull(Long usuarioId);

    List<InvitacionUsuario> findByUsedAtIsNullAndExpiresAtAfter(LocalDateTime expiresAfter);

    void deleteByUsuarioId(Long usuarioId);
}
