package com.tecozam.bills.tarifa.infrastructure.persistence;

import com.tecozam.bills.tarifa.domain.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, Long> {
    List<Tarifa> findByProveedorIdOrderByVigenteDesdeDesc(Long proveedorId);

    @Query("SELECT t FROM Tarifa t WHERE t.proveedor.id = :proveedorId AND t.vigenteDesde <= :fecha AND (t.vigenteHasta IS NULL OR t.vigenteHasta >= :fecha) ORDER BY t.vigenteDesde DESC")
    Optional<Tarifa> findVigenteEnFecha(@Param("proveedorId") Long proveedorId, @Param("fecha") LocalDate fecha);
}
