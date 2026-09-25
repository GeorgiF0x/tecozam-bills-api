package com.tecozam.bills.admin.infrastructure.persistence;

import com.tecozam.bills.admin.domain.ConfiguracionImport;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * La tabla configuracion_import es de fila unica (la migracion V26 la crea
 * con exactamente un registro y nunca se inserta una segunda fila desde la
 * aplicacion), asi que basta con JpaRepository.findAll() y tomar el primero.
 */
public interface ConfiguracionImportRepository extends JpaRepository<ConfiguracionImport, Long> {
}
