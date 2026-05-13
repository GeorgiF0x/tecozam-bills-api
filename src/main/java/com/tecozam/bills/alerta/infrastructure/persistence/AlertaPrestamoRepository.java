package com.tecozam.bills.alerta.infrastructure.persistence;

import com.tecozam.bills.alerta.domain.AlertaPrestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AlertaPrestamoRepository extends JpaRepository<AlertaPrestamo, Long> {

    List<AlertaPrestamo> findByLeidaFalseOrderByFechaAlertaDesc();

    List<AlertaPrestamo> findByLeidaFalseAndPrestamoTrabajadorIdOrderByFechaAlertaDesc(Long trabajadorId);

    long countByLeidaFalse();

    long countByLeidaFalseAndPrestamoTrabajadorId(Long trabajadorId);

    boolean existsByPrestamoIdAndTipoAlertaAndFechaAlerta(Long prestamoId, String tipoAlerta, LocalDate fechaAlerta);
}
