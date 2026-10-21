package com.tecozam.bills.factura.infrastructure.persistence;

import com.tecozam.bills.factura.domain.TarjetaResumen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TarjetaResumenRepository extends JpaRepository<TarjetaResumen, Long> {

    List<TarjetaResumen> findByFacturaId(Long facturaId);

    boolean existsByTarjetaId(Long tarjetaId);

    /**
     * Todos los TarjetaResumen (de cualquier factura, pasada o presente)
     * vinculados a la tarjeta del maestro con id {@code tarjetaId}. Usado por
     * la reclasificación automática TARJETA→VIAT (ver
     * odd/tasks/bugs-fecha-nan-y-reclasificacion-cepsa.md) para desvincular
     * (poner {@code tarjeta=null}) antes de borrar la tarjeta del maestro: el
     * cotejo no depende de este FK (usa {@code numTarjeta} como String), así
     * que desvincular es seguro y evita romper la referencia.
     */
    List<TarjetaResumen> findByTarjetaId(Long tarjetaId);
}
