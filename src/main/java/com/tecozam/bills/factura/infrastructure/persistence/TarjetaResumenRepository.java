package com.tecozam.bills.factura.infrastructure.persistence;

import com.tecozam.bills.factura.domain.TarjetaResumen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TarjetaResumenRepository extends JpaRepository<TarjetaResumen, Long> {

    List<TarjetaResumen> findByFacturaId(Long facturaId);
}
