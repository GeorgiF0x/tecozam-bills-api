package com.tecozam.bills.vehiculo.infrastructure.persistence;

import com.tecozam.bills.vehiculo.domain.Vehiculo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alta de vehiculo de sustitucion (odd/tasks/paquete-cotejo-b.md, S6): la
 * comprobacion de duplicados compara matriculas sin guiones, espacios ni
 * mayusculas, contra el SQL Server real (UPPER/REPLACE en HQL).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VehiculoRepositoryIT {

    @Autowired
    private VehiculoRepository vehiculoRepository;

    private Vehiculo guardar(String matricula) {
        return vehiculoRepository.save(Vehiculo.builder().matricula(matricula).tipo("Sin clasificar").build());
    }

    @Test
    @DisplayName("encuentra la matricula guardada con guion o espacio buscando su forma normalizada")
    void encuentraMatriculaGuardadaConGuionOEspacio() {
        Vehiculo conGuion = guardar("9911-it a");

        assertThat(vehiculoRepository.findActivosPorMatriculaNormalizada("9911ITA"))
                .extracting(Vehiculo::getId).containsExactly(conGuion.getId());
        assertThat(vehiculoRepository.findActivosPorMatriculaNormalizada("9911ITB")).isEmpty();
    }

    @Test
    @DisplayName("ignora los vehiculos borrados logicamente")
    void ignoraVehiculosBorrados() {
        Vehiculo borrado = guardar("9922-ITB");
        borrado.softDelete();
        vehiculoRepository.save(borrado);

        assertThat(vehiculoRepository.findActivosPorMatriculaNormalizada("9922ITB")).isEmpty();
    }
}
