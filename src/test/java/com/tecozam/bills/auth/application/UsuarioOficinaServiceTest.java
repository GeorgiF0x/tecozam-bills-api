package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.InvitacionUsuario;
import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.dto.AceptarInvitacionRequest;
import com.tecozam.bills.auth.dto.InvitacionPendienteDTO;
import com.tecozam.bills.auth.dto.InvitarUsuarioRequest;
import com.tecozam.bills.auth.infrastructure.persistence.InvitacionUsuarioRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.shared.infrastructure.email.EmailService;
import com.tecozam.bills.trabajador.application.TrabajadorResolver;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioOficinaServiceTest {

    @Mock UsuarioOficinaRepository usuarioOficinaRepository;
    @Mock TrabajadorRepository trabajadorRepository;
    @Mock TrabajadorResolver trabajadorResolver;
    @Mock org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock InvitacionUsuarioRepository invitacionUsuarioRepository;
    @Mock EmailService emailService;

    UsuarioOficinaService service;

    private static final String WEB_BASE_URL = "http://localhost:3000";

    @BeforeEach
    void setUp() {
        service = new UsuarioOficinaService(
                usuarioOficinaRepository,
                trabajadorRepository,
                trabajadorResolver,
                passwordEncoder,
                invitacionUsuarioRepository,
                emailService,
                WEB_BASE_URL);
    }

    // ── invitar ──────────────────────────────────────────────────────

    @Test
    @DisplayName("invitar crea usuario ADMIN inactivo, guarda invitacion y envia email")
    void invitar_casoFeliz() {
        UsuarioOficina invitador = UsuarioOficina.builder()
                .username("admin1").rol(Rol.ADMIN).activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO).build();
        invitador.setId(1L);

        when(usuarioOficinaRepository.existsByEmailAndActivoTrue("nuevo@tecozam.com")).thenReturn(false);
        when(usuarioOficinaRepository.findByUsername("admin1")).thenReturn(Optional.of(invitador));
        when(usuarioOficinaRepository.save(any(UsuarioOficina.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invitacionUsuarioRepository.save(any(InvitacionUsuario.class))).thenAnswer(inv -> {
            InvitacionUsuario i = inv.getArgument(0);
            i.setId(900L);
            return i;
        });

        InvitarUsuarioRequest req = new InvitarUsuarioRequest("Nueva Admin", "nuevo@tecozam.com");
        InvitacionPendienteDTO resultado = service.invitar(req, "admin1");

        assertThat(resultado.id()).isEqualTo(900L);
        assertThat(resultado.nombre()).isEqualTo("Nueva Admin");
        assertThat(resultado.email()).isEqualTo("nuevo@tecozam.com");
        assertThat(resultado.invitadoPorUsername()).isEqualTo("admin1");
        assertThat(resultado.expiresAt()).isAfter(LocalDateTime.now().plusHours(71));

        ArgumentCaptor<UsuarioOficina> usuarioCaptor = ArgumentCaptor.forClass(UsuarioOficina.class);
        verify(usuarioOficinaRepository).save(usuarioCaptor.capture());
        UsuarioOficina creado = usuarioCaptor.getValue();
        assertThat(creado.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(creado.isActivo()).isFalse();
        assertThat(creado.getEmail()).isEqualTo("nuevo@tecozam.com");
        assertThat(creado.getNombreCompleto()).isEqualTo("Nueva Admin");

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarInvitacionAdmin(eq("nuevo@tecozam.com"), eq("Nueva Admin"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith(WEB_BASE_URL + "/invitacion/");
        assertThat(linkCaptor.getValue().substring((WEB_BASE_URL + "/invitacion/").length())).isNotBlank();
    }

    @Test
    @DisplayName("invitar rechaza si el email ya pertenece a un usuario ACTIVO")
    void invitar_emailYaActivo_rechazado() {
        when(usuarioOficinaRepository.existsByEmailAndActivoTrue("activo@tecozam.com")).thenReturn(true);

        InvitarUsuarioRequest req = new InvitarUsuarioRequest("Alguien", "activo@tecozam.com");

        assertThatThrownBy(() -> service.invitar(req, "admin1"))
                .isInstanceOf(BusinessException.class);

        verify(usuarioOficinaRepository, never()).save(any());
        verify(invitacionUsuarioRepository, never()).save(any());
        verify(emailService, never()).enviarInvitacionAdmin(any(), any(), any());
    }

    // ── aceptarInvitacion ────────────────────────────────────────────

    @Test
    @DisplayName("aceptarInvitacion con token valido activa el usuario y marca la invitacion usada")
    void aceptarInvitacion_tokenValido() {
        String tokenEnClaro = "token-de-prueba-123";
        String tokenHashEsperado = sha256(tokenEnClaro);

        UsuarioOficina usuario = UsuarioOficina.builder()
                .username("invitado@tecozam.com").rol(Rol.ADMIN).activo(false)
                .estadoRegistro(EstadoRegistro.PENDIENTE).build();
        usuario.setId(10L);

        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(usuario)
                .tokenHash(tokenHashEsperado)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        invitacion.setId(900L);

        when(invitacionUsuarioRepository.findByTokenHash(tokenHashEsperado)).thenReturn(Optional.of(invitacion));
        when(passwordEncoder.encode("Password123")).thenReturn("ENC(Password123)");

        service.aceptarInvitacion(tokenEnClaro, new AceptarInvitacionRequest("Password123"));

        assertThat(usuario.getPassword()).isEqualTo("ENC(Password123)");
        assertThat(usuario.isActivo()).isTrue();
        assertThat(usuario.getEstadoRegistro()).isEqualTo(EstadoRegistro.ACTIVO);
        assertThat(invitacion.getUsedAt()).isNotNull();
        verify(usuarioOficinaRepository).save(usuario);
        verify(invitacionUsuarioRepository).save(invitacion);
    }

    @Test
    @DisplayName("aceptarInvitacion rechaza token caducado")
    void aceptarInvitacion_tokenCaducado_rechazado() {
        String tokenEnClaro = "token-caducado";
        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(UsuarioOficina.builder().activo(false).rol(Rol.ADMIN).build())
                .tokenHash(sha256(tokenEnClaro))
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(invitacionUsuarioRepository.findByTokenHash(sha256(tokenEnClaro))).thenReturn(Optional.of(invitacion));

        assertThatThrownBy(() -> service.aceptarInvitacion(tokenEnClaro, new AceptarInvitacionRequest("Password123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("caducado");

        verify(usuarioOficinaRepository, never()).save(any());
    }

    @Test
    @DisplayName("aceptarInvitacion rechaza token ya usado")
    void aceptarInvitacion_tokenYaUsado_rechazado() {
        String tokenEnClaro = "token-usado";
        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(UsuarioOficina.builder().activo(false).rol(Rol.ADMIN).build())
                .tokenHash(sha256(tokenEnClaro))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .usedAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(invitacionUsuarioRepository.findByTokenHash(sha256(tokenEnClaro))).thenReturn(Optional.of(invitacion));

        assertThatThrownBy(() -> service.aceptarInvitacion(tokenEnClaro, new AceptarInvitacionRequest("Password123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("utilizada");

        verify(usuarioOficinaRepository, never()).save(any());
    }

    @Test
    @DisplayName("aceptarInvitacion rechaza si el usuario ya no esta PENDIENTE (defensa en profundidad)")
    void aceptarInvitacion_usuarioYaNoPendiente_rechazado() {
        String tokenEnClaro = "token-defensa";
        String tokenHashEsperado = sha256(tokenEnClaro);

        UsuarioOficina usuarioYaActivo = UsuarioOficina.builder()
                .username("yaactivo@tecozam.com").rol(Rol.ADMIN)
                .activo(true).estadoRegistro(EstadoRegistro.ACTIVO).build();
        usuarioYaActivo.setId(99L);

        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(usuarioYaActivo)
                .tokenHash(tokenHashEsperado)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(invitacionUsuarioRepository.findByTokenHash(tokenHashEsperado)).thenReturn(Optional.of(invitacion));

        assertThatThrownBy(() -> service.aceptarInvitacion(tokenEnClaro, new AceptarInvitacionRequest("Password123")))
                .isInstanceOf(BusinessException.class);

        verify(usuarioOficinaRepository, never()).save(any());
        verify(invitacionUsuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("aceptarInvitacion rechaza token desconocido sin revelar detalles")
    void aceptarInvitacion_tokenDesconocido_rechazado() {
        when(invitacionUsuarioRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aceptarInvitacion("token-que-no-existe", new AceptarInvitacionRequest("Password123")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invitación no válida");

        verify(usuarioOficinaRepository, never()).save(any());
    }

    @Test
    @DisplayName("AceptarInvitacionRequest rechaza password debil por validacion de bean")
    void aceptarInvitacionRequest_passwordDebil_esRechazadaPorValidacion() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<AceptarInvitacionRequest>> violaciones =
                    validator.validate(new AceptarInvitacionRequest("abc"));
            assertThat(violaciones).isNotEmpty();
        }
    }

    // ── reenviarInvitacion ───────────────────────────────────────────

    @Test
    @DisplayName("reenviarInvitacion invalida la vieja, crea una nueva y reenvia el email")
    void reenviarInvitacion_casoBasico() {
        UsuarioOficina invitador = UsuarioOficina.builder().username("admin1").rol(Rol.ADMIN).activo(true).build();
        invitador.setId(1L);
        UsuarioOficina usuario = UsuarioOficina.builder()
                .username("invitado@tecozam.com").email("invitado@tecozam.com")
                .nombreCompleto("Invitado Uno").rol(Rol.ADMIN).activo(false).build();
        usuario.setId(10L);

        InvitacionUsuario vieja = InvitacionUsuario.builder()
                .usuario(usuario).invitadoPor(invitador)
                .tokenHash("hash-vieja")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        vieja.setId(800L);

        when(invitacionUsuarioRepository.findById(800L)).thenReturn(Optional.of(vieja));
        when(invitacionUsuarioRepository.save(any(InvitacionUsuario.class))).thenAnswer(inv -> {
            InvitacionUsuario i = inv.getArgument(0);
            if (i.getId() == null) {
                i.setId(801L);
            }
            return i;
        });

        InvitacionPendienteDTO resultado = service.reenviarInvitacion(800L);

        assertThat(vieja.getUsedAt()).isNotNull();
        assertThat(resultado.id()).isEqualTo(801L);
        assertThat(resultado.email()).isEqualTo("invitado@tecozam.com");
        assertThat(resultado.invitadoPorUsername()).isEqualTo("admin1");

        verify(emailService).enviarInvitacionAdmin(eq("invitado@tecozam.com"), eq("Invitado Uno"), anyString());
    }

    @Test
    @DisplayName("reenviarInvitacion rechaza una invitacion ya aceptada (evita toma de cuenta)")
    void reenviarInvitacion_invitacionYaAceptada_rechazada() {
        UsuarioOficina adminActivo = UsuarioOficina.builder()
                .username("admin@tecozam.com").email("admin@tecozam.com")
                .nombreCompleto("Admin Real").rol(Rol.ADMIN)
                .activo(true).estadoRegistro(EstadoRegistro.ACTIVO).build();
        adminActivo.setId(88L);

        InvitacionUsuario viejaAceptada = InvitacionUsuario.builder()
                .usuario(adminActivo)
                .tokenHash("hash-aceptada")
                .expiresAt(LocalDateTime.now().minusDays(5))
                .usedAt(LocalDateTime.now().minusDays(4))
                .build();
        viejaAceptada.setId(601L);

        when(invitacionUsuarioRepository.findById(601L)).thenReturn(Optional.of(viejaAceptada));

        assertThatThrownBy(() -> service.reenviarInvitacion(601L))
                .isInstanceOf(BusinessException.class);

        verify(invitacionUsuarioRepository, never()).save(any());
        verify(emailService, never()).enviarInvitacionAdmin(any(), any(), any());
    }

    @Test
    @DisplayName("reenviarInvitacion lanza ResourceNotFoundException si no existe")
    void reenviarInvitacion_noExiste() {
        when(invitacionUsuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reenviarInvitacion(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── cancelarInvitacion ───────────────────────────────────────────

    @Test
    @DisplayName("cancelarInvitacion de un usuario pendiente lo borra para liberar el email")
    void cancelarInvitacion_usuarioPendiente_borraUsuarioYLiberaElEmail() {
        UsuarioOficina usuarioPendiente = UsuarioOficina.builder()
                .username("pendiente@tecozam.com").email("pendiente@tecozam.com")
                .rol(Rol.ADMIN).activo(false).estadoRegistro(EstadoRegistro.PENDIENTE).build();
        usuarioPendiente.setId(55L);

        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(usuarioPendiente)
                .tokenHash("hash-x")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        invitacion.setId(700L);

        when(invitacionUsuarioRepository.findById(700L)).thenReturn(Optional.of(invitacion));

        service.cancelarInvitacion(700L);

        verify(invitacionUsuarioRepository).deleteByUsuarioId(55L);
        verify(usuarioOficinaRepository).delete(usuarioPendiente);
        verify(invitacionUsuarioRepository, never()).save(any());

        // El email queda libre: una nueva invitacion al mismo email no debe
        // tropezar con el usuario pendiente ya borrado.
        when(usuarioOficinaRepository.existsByEmailAndActivoTrue("pendiente@tecozam.com")).thenReturn(false);
        when(usuarioOficinaRepository.findByUsername("admin1"))
                .thenReturn(Optional.of(UsuarioOficina.builder().username("admin1").build()));
        when(usuarioOficinaRepository.save(any(UsuarioOficina.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invitacionUsuarioRepository.save(any(InvitacionUsuario.class))).thenAnswer(inv -> inv.getArgument(0));

        service.invitar(new InvitarUsuarioRequest("Pendiente", "pendiente@tecozam.com"), "admin1");
    }

    @Test
    @DisplayName("cancelarInvitacion de un usuario ya activo solo invalida la invitacion (no borra el usuario)")
    void cancelarInvitacion_usuarioYaActivo_soloInvalidaInvitacion() {
        UsuarioOficina usuarioActivo = UsuarioOficina.builder()
                .activo(true).estadoRegistro(EstadoRegistro.ACTIVO).rol(Rol.ADMIN).build();
        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(usuarioActivo)
                .tokenHash("hash-x")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        invitacion.setId(700L);

        when(invitacionUsuarioRepository.findById(700L)).thenReturn(Optional.of(invitacion));

        service.cancelarInvitacion(700L);

        assertThat(invitacion.getUsedAt()).isNotNull();
        verify(invitacionUsuarioRepository).save(invitacion);
        verify(usuarioOficinaRepository, never()).delete(any());
        verify(invitacionUsuarioRepository, never()).deleteByUsuarioId(any());
    }

    @Test
    @DisplayName("cancelarInvitacion rechaza una invitacion ya usada y no borra ni toca nada")
    void cancelarInvitacion_invitacionYaUsada_rechazaYNoBorraNada() {
        UsuarioOficina adminDesactivado = UsuarioOficina.builder()
                .username("exadmin@tecozam.com").rol(Rol.ADMIN)
                .activo(false) // desactivado despues de haber sido admin real
                .estadoRegistro(EstadoRegistro.ACTIVO)
                .build();
        adminDesactivado.setId(77L);

        InvitacionUsuario invitacionVieja = InvitacionUsuario.builder()
                .usuario(adminDesactivado)
                .tokenHash("hash-vieja-usada")
                .expiresAt(LocalDateTime.now().minusDays(10))
                .usedAt(LocalDateTime.now().minusDays(9))
                .build();
        invitacionVieja.setId(701L);

        when(invitacionUsuarioRepository.findById(701L)).thenReturn(Optional.of(invitacionVieja));

        assertThatThrownBy(() -> service.cancelarInvitacion(701L))
                .isInstanceOf(BusinessException.class);

        verify(usuarioOficinaRepository, never()).delete(any());
        verify(invitacionUsuarioRepository, never()).deleteByUsuarioId(any());
        verify(invitacionUsuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelarInvitacion lanza ResourceNotFoundException si no existe")
    void cancelarInvitacion_noExiste() {
        when(invitacionUsuarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelarInvitacion(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── findInvitacionesPendientes ──────────────────────────────────

    @Test
    @DisplayName("findInvitacionesPendientes mapea las invitaciones no usadas y vigentes")
    void findInvitacionesPendientes_mapeaCorrectamente() {
        UsuarioOficina invitador = UsuarioOficina.builder().username("admin1").rol(Rol.ADMIN).activo(true).build();
        UsuarioOficina usuario = UsuarioOficina.builder()
                .username("x@tecozam.com").email("x@tecozam.com").nombreCompleto("X Y").rol(Rol.ADMIN).build();

        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(usuario).invitadoPor(invitador)
                .tokenHash("hash-pend")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        invitacion.setId(1L);

        when(invitacionUsuarioRepository.findByUsedAtIsNullAndExpiresAtAfter(any()))
                .thenReturn(java.util.List.of(invitacion));

        var resultado = service.findInvitacionesPendientes();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).email()).isEqualTo("x@tecozam.com");
        assertThat(resultado.get(0).invitadoPorUsername()).isEqualTo("admin1");
    }

    // ── eliminar ─────────────────────────────────────────────────────

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra el registro")
    void eliminar_hacesBorradoLogico() {
        UsuarioOficina usuario = UsuarioOficina.builder()
                .username("borrar1").rol(Rol.GESTOR).activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO).build();
        usuario.setId(500L);

        when(usuarioOficinaRepository.findById(500L)).thenReturn(Optional.of(usuario));
        when(usuarioOficinaRepository.save(any(UsuarioOficina.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(500L, false);

        assertThat(usuario.isEliminado()).isTrue();
        verify(usuarioOficinaRepository).save(usuario);
        verify(usuarioOficinaRepository, never()).delete(any(UsuarioOficina.class));
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si el usuario no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        UsuarioOficina usuario = UsuarioOficina.builder()
                .username("borrar2").rol(Rol.GESTOR).activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO).build();
        usuario.setId(501L);

        when(usuarioOficinaRepository.findById(501L)).thenReturn(Optional.of(usuario));
        when(invitacionUsuarioRepository.existsByUsuarioId(501L)).thenReturn(false);
        when(invitacionUsuarioRepository.existsByInvitadoPorId(501L)).thenReturn(false);

        service.eliminar(501L, true);

        verify(usuarioOficinaRepository).delete(usuario);
        verify(usuarioOficinaRepository, never()).save(any(UsuarioOficina.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si el usuario tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        UsuarioOficina usuario = UsuarioOficina.builder()
                .username("borrar3").rol(Rol.GESTOR).activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO).build();
        usuario.setId(502L);

        when(usuarioOficinaRepository.findById(502L)).thenReturn(Optional.of(usuario));
        when(invitacionUsuarioRepository.existsByUsuarioId(502L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(502L, true))
                .isInstanceOf(BusinessException.class);
        verify(usuarioOficinaRepository, never()).delete(any(UsuarioOficina.class));
        verify(usuarioOficinaRepository, never()).save(any(UsuarioOficina.class));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
