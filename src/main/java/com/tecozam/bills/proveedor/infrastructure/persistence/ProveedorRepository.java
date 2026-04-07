package com.tecozam.bills.proveedor.infrastructure.persistence;

import com.tecozam.bills.proveedor.domain.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    List<Proveedor> findByActivoTrue();

    Optional<Proveedor> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
