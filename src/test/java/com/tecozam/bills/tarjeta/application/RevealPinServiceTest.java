package com.tecozam.bills.tarjeta.application;

import com.tecozam.bills.auditoria.application.AuditoriaPinService;
import com.tecozam.bills.auditoria.domain.MetodoPinAcceso;
import com.tecozam.bills.auditoria.domain.ResultadoPinAcceso;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.tarjeta.dto.RevealPinRequest;
import com.tecozam.bills.tarjeta.dto.RevealPinResponse;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.webauthn.application.WebauthnAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevealPinServiceTest {

    @Mock TarjetaRepository tarjetaRepo;
    @Mock TarjetaAsignacionRepository asignacionRepo;
    @Mock UsuarioCampoRepository userRepo;
    @Mock WebauthnAuthenticationService webauthn;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditoriaPinService auditoria;

    RevealPinService service;

    UsuarioCampo usuario;
    Trabajador trabajador;
    Tarjeta tarjeta;

    @BeforeEach
    void setUp() {
        service = new RevealPinService(
                tarjetaRepo, asignacionRepo, userRepo, webauthn, passwordEncoder, auditoria);

        trabajador = new Trabajador();
        trabajador.setId(500L);

        usuario = new UsuarioCampo();
        usuario.setId(42L);
        usuario.setUsername("campo");
        usuario.setPassword("$2a$10$hash");
        usuario.setTrabajador(trabajador);

        tarjeta = Tarjeta.builder().pinEncrypted("1234").build();
        tarjeta.setId(99L);
    }

    @Test
    @DisplayName("Happy path biometría: assertion válida → devuelve PIN + audita OK")
    void reveal_biometria_ok() {
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(usuario));
        when(tarjetaRepo.findById(99L)).thenReturn(Optional.of(tarjeta));
        when(asignacionRepo.findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(99L, 500L))
                .thenReturn(Optional.of(new TarjetaAsignacion()));
        when(webauthn.verifyAssertion("tok", "{}")).thenReturn(42L);

        RevealPinRequest req = new RevealPinRequest(
                new RevealPinRequest.AssertionPart("tok", "{}"), null);
        RevealPinResponse resp = service.reveal(99L, req, "campo", "1.2.3.4", "Mozilla");

        assertThat(resp.pin()).isEqualTo("1234");
        assertThat(resp.expiresIn()).isEqualTo(30);
        verify(auditoria).registrar(42L, 99L, MetodoPinAcceso.BIOMETRIA, ResultadoPinAcceso.OK, "1.2.3.4", "Mozilla");
    }

    @Test
    @DisplayName("Happy path password: contraseña correcta → devuelve PIN + audita OK")
    void reveal_password_ok() {
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(usuario));
        when(tarjetaRepo.findById(99L)).thenReturn(Optional.of(tarjeta));
        when(asignacionRepo.findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(99L, 500L))
                .thenReturn(Optional.of(new TarjetaAsignacion()));
        when(passwordEncoder.matches("clave", "$2a$10$hash")).thenReturn(true);

        RevealPinRequest req = new RevealPinRequest(null, "clave");
        RevealPinResponse resp = service.reveal(99L, req, "campo", "1.2.3.4", "Mozilla");

        assertThat(resp.pin()).isEqualTo("1234");
        verify(auditoria).registrar(42L, 99L, MetodoPinAcceso.PASSWORD, ResultadoPinAcceso.OK, "1.2.3.4", "Mozilla");
    }

    @Test
    @DisplayName("Assertion fabricada → 401 + audita FAILED")
    void reveal_assertionMala() {
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(usuario));
        when(tarjetaRepo.findById(99L)).thenReturn(Optional.of(tarjeta));
        when(asignacionRepo.findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(99L, 500L))
                .thenReturn(Optional.of(new TarjetaAsignacion()));
        when(webauthn.verifyAssertion("tok", "{}"))
                .thenThrow(new BusinessException("Assertion WebAuthn rechazada"));

        RevealPinRequest req = new RevealPinRequest(
                new RevealPinRequest.AssertionPart("tok", "{}"), null);

        assertThatThrownBy(() -> service.reveal(99L, req, "campo", "1.2.3.4", "Mozilla"))
                .isInstanceOf(BusinessException.class);
        verify(auditoria).registrar(42L, 99L, MetodoPinAcceso.BIOMETRIA, ResultadoPinAcceso.FAILED, "1.2.3.4", "Mozilla");
    }

    @Test
    @DisplayName("Sin asignación activa → 403 + NO audita (no llegamos al método)")
    void reveal_sinAsignacion() {
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(usuario));
        when(tarjetaRepo.findById(99L)).thenReturn(Optional.of(tarjeta));
        when(asignacionRepo.findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(99L, 500L))
                .thenReturn(Optional.empty());

        RevealPinRequest req = new RevealPinRequest(null, "clave");

        assertThatThrownBy(() -> service.reveal(99L, req, "campo", "1.2.3.4", "Mozilla"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("asignación");
        verify(auditoria, never()).registrar(anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Body vacío (sin assertion ni password) → 400")
    void reveal_bodyInvalido() {
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(usuario));
        when(tarjetaRepo.findById(99L)).thenReturn(Optional.of(tarjeta));
        when(asignacionRepo.findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(99L, 500L))
                .thenReturn(Optional.of(new TarjetaAsignacion()));

        RevealPinRequest req = new RevealPinRequest(null, null);

        assertThatThrownBy(() -> service.reveal(99L, req, "campo", "1.2.3.4", "Mozilla"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
