package com.tecozam.bills.auditoria.infrastructure.persistence;

import com.tecozam.bills.auditoria.domain.PinAccesoEvento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PinAccesoEventoRepository extends JpaRepository<PinAccesoEvento, Long> {

    List<PinAccesoEvento> findByTarjetaIdOrderByTimestampDesc(Long tarjetaId);

    List<PinAccesoEvento> findByUsuarioCampoIdOrderByTimestampDesc(Long usuarioCampoId);
}
