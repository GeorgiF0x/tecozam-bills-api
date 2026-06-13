package com.tecozam.bills.tarjeta.application;

import com.tecozam.bills.auditoria.application.AuditoriaPinService;
import com.tecozam.bills.auditoria.domain.MetodoPinAcceso;
import com.tecozam.bills.auditoria.domain.ResultadoPinAcceso;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.dto.RevealPinRequest;
import com.tecozam.bills.tarjeta.dto.RevealPinResponse;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.webauthn.application.WebauthnAuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revela el PIN de una tarjeta a su conductor asignado tras validar biometría
 * (WebAuthn) o, como fallback, su contraseña. Cada intento (OK o FAILED)
 * queda registrado en {@code auditoria_pin_acceso}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevealPinService {

    public static final int PIN_VISIBLE_SECONDS = 30;

    private final TarjetaRepository tarjetaRepo;
    private final TarjetaAsignacionRepository asignacionRepo;
    private final UsuarioCampoRepository userRepo;
    private final WebauthnAuthenticationService webauthn;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaPinService auditoria;

    @Transactional
    public RevealPinResponse reveal(Long tarjetaId, RevealPinRequest body, String username,
                                    String ip, String userAgent) {
        UsuarioCampo u = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", username));

        Tarjeta tarjeta = tarjetaRepo.findById(tarjetaId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", tarjetaId));

        Trabajador t = u.getTrabajador();
        if (t == null) {
            throw new BusinessException("El usuario no tiene un trabajador asociado");
        }
        asignacionRepo.findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(tarjetaId, t.getId())
                .orElseThrow(() -> new BusinessException(
                        "No tienes asignación activa para esta tarjeta"));

        if (tarjeta.getPinEncrypted() == null || tarjeta.getPinEncrypted().isBlank()) {
            throw new BusinessException("La tarjeta no tiene un PIN guardado");
        }

        // Decidir método y verificar
        boolean haveAssertion = body.assertion() != null
                && body.assertion().token() != null
                && !body.assertion().token().isBlank();
        boolean havePassword = body.password() != null && !body.password().isBlank();

        if (!haveAssertion && !havePassword) {
            throw new IllegalArgumentException(
                    "Se requiere assertion biométrica o password en el body");
        }

        MetodoPinAcceso metodo = haveAssertion ? MetodoPinAcceso.BIOMETRIA : MetodoPinAcceso.PASSWORD;

        try {
            if (haveAssertion) {
                webauthn.verifyAssertion(body.assertion().token(), body.assertion().credentialJson());
            } else {
                if (!passwordEncoder.matches(body.password(), u.getPassword())) {
                    throw new BusinessException("Contraseña incorrecta");
                }
            }
        } catch (RuntimeException ex) {
            auditoria.registrar(u.getId(), tarjetaId, metodo, ResultadoPinAcceso.FAILED, ip, userAgent);
            throw ex;
        }

        auditoria.registrar(u.getId(), tarjetaId, metodo, ResultadoPinAcceso.OK, ip, userAgent);
        log.info("PIN revelado tarjetaId={} userId={} metodo={}", tarjetaId, u.getId(), metodo);
        return new RevealPinResponse(tarjeta.getPinEncrypted(), PIN_VISIBLE_SECONDS);
    }
}
