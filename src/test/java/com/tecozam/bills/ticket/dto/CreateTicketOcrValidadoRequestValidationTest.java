package com.tecozam.bills.ticket.dto;

import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FLEET-01: el campo pin del request /ocr-validado debe ser opcional.
 * Sólo se valida formato (4 dígitos) cuando el cliente lo envía.
 */
class CreateTicketOcrValidadoRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CreateTicketOcrValidadoRequest baseRequest(String pin) {
        return new CreateTicketOcrValidadoRequest(
                1L,
                pin,
                CategoriaRecurso.VEHICULO,
                1L,
                1L,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    @Test
    @DisplayName("FLEET-01: PIN null no produce violación")
    void pinNullNoEsRechazado() {
        Set<ConstraintViolation<CreateTicketOcrValidadoRequest>> v =
                validator.validate(baseRequest(null));

        assertThat(v)
                .as("Con pin=null no debe haber violación en el campo pin")
                .filteredOn(cv -> cv.getPropertyPath().toString().equals("pin"))
                .isEmpty();
    }

    @Test
    @DisplayName("PIN con 4 dígitos válido pasa")
    void pinValidoPasa() {
        Set<ConstraintViolation<CreateTicketOcrValidadoRequest>> v =
                validator.validate(baseRequest("1234"));

        assertThat(v)
                .filteredOn(cv -> cv.getPropertyPath().toString().equals("pin"))
                .isEmpty();
    }

    @Test
    @DisplayName("PIN mal formado (no 4 dígitos) sigue siendo rechazado por @Pattern")
    void pinMalFormadoEsRechazado() {
        Set<ConstraintViolation<CreateTicketOcrValidadoRequest>> v =
                validator.validate(baseRequest("12"));

        assertThat(v)
                .filteredOn(cv -> cv.getPropertyPath().toString().equals("pin"))
                .isNotEmpty();
    }
}
