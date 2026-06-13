package com.tecozam.bills.webauthn.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Config de WebAuthn / FIDO2. Valores override via env:
 *   APP_WEBAUTHN_ENABLED=true|false
 *   APP_WEBAUTHN_RP_ID=fleet.z-innova.com
 *   APP_WEBAUTHN_RP_NAME=Tecozam Operarios
 *   APP_WEBAUTHN_ORIGINS=https://fleet.z-innova.com,https://app.tecozam.com
 */
@ConfigurationProperties(prefix = "app.webauthn")
@Data
public class WebauthnProperties {

    /** Si false, /pin/reveal acepta sólo password (fallback puro). */
    private boolean enabled = true;

    /** Relying Party ID — debe coincidir con el host de la PWA (sin protocolo ni puerto). */
    private String rpId = "localhost";

    private String rpName = "Tecozam Operarios";

    /** Orígenes aceptados para la PWA. */
    private Set<String> origins = new LinkedHashSet<>(Set.of("http://localhost:3000"));
}
