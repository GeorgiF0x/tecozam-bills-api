package com.tecozam.bills.factura.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class FacturaTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onPrePersist_asignaCreadoPorYModificadoPorDesdeElUsuarioAutenticado() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("gestor1", null, java.util.List.of()));

        Factura factura = Factura.builder().build();
        factura.onPrePersist();

        assertThat(factura.getCreadoPor()).isEqualTo("gestor1");
        assertThat(factura.getModificadoPor()).isEqualTo("gestor1");
    }

    @Test
    void onPrePersist_usaSystemCuandoNoHayUsuarioAutenticado() {
        SecurityContextHolder.clearContext();

        Factura factura = Factura.builder().build();
        factura.onPrePersist();

        assertThat(factura.getCreadoPor()).isEqualTo("SYSTEM");
    }
}
