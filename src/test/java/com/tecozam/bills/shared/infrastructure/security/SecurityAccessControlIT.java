package com.tecozam.bills.shared.infrastructure.security;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre 3 vulnerabilidades reales confirmadas EN VIVO contra el entorno demo
 * (bills.z-innova.com) esta misma sesion, preexistentes desde el commit
 * inicial del repo (cb54870, 7-abril-2026):
 * - GET /api/tickets y /api/tickets/{id} sin @PreAuthorize: cualquier rol
 *   autenticado (incluido ROLE_CAMPO) podia leer todos los tickets.
 * - GET /api/operaciones y /api/operaciones/factura/{id} sin @PreAuthorize:
 *   mismo problema con las operaciones de factura.
 * - Un refresh token (hasta 7 dias de vida) era indistinguible de un access
 *   token y servia tal cual como Authorization: Bearer contra cualquier
 *   endpoint protegido, sin pasar por /refresh.
 *
 * No existia ninguna prueba a nivel HTTP/seguridad en todo el proyecto antes
 * de esta clase (ni de @PreAuthorize ni de los flujos de login/refresh).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityAccessControlIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UsuarioOficinaRepository usuarioOficinaRepository;

    @Autowired
    private UsuarioCampoRepository usuarioCampoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "SecTest123x";

    private Long adminId;
    private Long campoId;
    private String adminAccessToken;
    private String adminRefreshToken;
    private String campoAccessToken;
    private String campoRefreshToken;

    @BeforeEach
    void seedUsersAndLogin() {
        UsuarioOficina admin = usuarioOficinaRepository.save(UsuarioOficina.builder()
                .username("sectest_admin_it")
                .password(passwordEncoder.encode(PASSWORD))
                .rol(Rol.ADMIN)
                .activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO)
                .build());
        adminId = admin.getId();

        UsuarioCampo campo = usuarioCampoRepository.save(UsuarioCampo.builder()
                .username("sectest_campo_it")
                .password(passwordEncoder.encode(PASSWORD))
                .activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO)
                .build());
        campoId = campo.getId();

        Map<String, Object> adminTokens = login("/api/auth/oficina/login", "sectest_admin_it");
        adminAccessToken = (String) adminTokens.get("accessToken");
        adminRefreshToken = (String) adminTokens.get("refreshToken");

        Map<String, Object> campoTokens = login("/api/auth/campo/login", "sectest_campo_it");
        campoAccessToken = (String) campoTokens.get("accessToken");
        campoRefreshToken = (String) campoTokens.get("refreshToken");
    }

    @AfterEach
    void cleanup() {
        usuarioOficinaRepository.deleteById(adminId);
        usuarioCampoRepository.deleteById(campoId);
    }

    @Test
    void campoNoPuedeListarTickets() {
        assertThat(getWithToken("/api/tickets", campoAccessToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void campoNoPuedeLeerUnTicketConcreto() {
        assertThat(getWithToken("/api/tickets/1", campoAccessToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminSiPuedeListarTickets() {
        assertThat(getWithToken("/api/tickets", adminAccessToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void campoNoPuedeListarOperaciones() {
        assertThat(getWithToken("/api/operaciones", campoAccessToken).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminSiPuedeListarOperaciones() {
        assertThat(getWithToken("/api/operaciones", adminAccessToken).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void refreshTokenNoSirveComoAccessTokenContraEndpointProtegido() {
        // Usa el refresh token de ADMIN contra un endpoint que ADMIN SI puede
        // ver (/api/tickets) — si solo probara con el token de CAMPO, un 403
        // podria deberse al chequeo de rol (VULN-001/002) en vez de a este
        // chequeo de tipo de token, dando una falsa sensacion de cobertura.
        ResponseEntity<String> res = getWithToken("/api/tickets", adminRefreshToken);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        assertThat(res.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    void refreshOficinaSigueFuncionandoConUnRefreshTokenReal() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/auth/oficina/refresh", Map.of("refreshToken", adminRefreshToken), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("accessToken")).isNotNull();
    }

    @Test
    void refreshCampoSigueFuncionandoConUnRefreshTokenReal() {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                "/api/auth/campo/refresh", Map.of("refreshToken", campoRefreshToken), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("accessToken")).isNotNull();
    }

    private Map<String, Object> login(String path, String username) {
        ResponseEntity<Map> res = restTemplate.postForEntity(
                path, Map.of("username", username, "password", PASSWORD), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private ResponseEntity<String> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
