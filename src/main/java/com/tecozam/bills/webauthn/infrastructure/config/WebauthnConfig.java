package com.tecozam.bills.webauthn.infrastructure.config;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de WebAuthn / FIDO2.
 *
 * Registra {@link WebauthnProperties} y construye el {@link RelyingParty}
 * de yubico con el {@link CredentialRepository} adapter del proyecto.
 */
@Configuration
@EnableConfigurationProperties(WebauthnProperties.class)
public class WebauthnConfig {

    @Bean
    public RelyingParty relyingParty(WebauthnProperties props, CredentialRepository credRepo) {
        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
                .id(props.getRpId())
                .name(props.getRpName())
                .build();
        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credRepo)
                .origins(props.getOrigins())
                .build();
    }
}
