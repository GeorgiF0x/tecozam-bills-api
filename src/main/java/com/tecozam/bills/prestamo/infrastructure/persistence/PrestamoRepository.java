package com.tecozam.bills.prestamo.infrastructure.persistence;

import com.tecozam.bills.prestamo.domain.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    List<Prestamo> findByEstado(String estado);

    List<Prestamo> findByTrabajadorId(Long trabajadorId);

    List<Prestamo> findByEstadoAndFechaFinPrevistaBefore(String estado, LocalDate date);

    long countByEstado(String estado);
}
