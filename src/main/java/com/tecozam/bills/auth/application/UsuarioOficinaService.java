package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.InvitacionUsuario;
import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.dto.AceptarInvitacionRequest;
import com.tecozam.bills.auth.dto.CrearUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.EditarUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.InvitacionPendienteDTO;
import com.tecozam.bills.auth.dto.InvitarUsuarioRequest;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
import com.tecozam.bills.auth.infrastructure.persistence.InvitacionUsuarioRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import com.tecozam.bills.shared.infrastructure.email.EmailService;
import com.tecozam.bills.shared.util.NombreApellidosSplitter;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.trabajador.application.TrabajadorResolver;
import com.tecozam.bills.trabajador.domain.OrigenTrabajador;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class UsuarioOficinaService {

    private static final int HORAS_VALIDEZ_INVITACION = 72;

    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorResolver trabajadorResolver;
    private final PasswordEncoder passwordEncoder;
    private final InvitacionUsuarioRepository invitacionUsuarioRepository;
    private final EmailService emailService;
    private final String webBaseUrl;

    // shortcut: constructor explicito (no @RequiredArgsConstructor) porque
    // Lombok no copia @Value al parametro generado; mismo patron que SmtpEmailService.
    public UsuarioOficinaService(
            UsuarioOficinaRepository usuarioOficinaRepository,
            TrabajadorRepository trabajadorRepository,
            TrabajadorResolver trabajadorResolver,
            PasswordEncoder passwordEncoder,
            InvitacionUsuarioRepository invitacionUsuarioRepository,
            EmailService emailService,
            @Value("${app.web.base-url}") String webBaseUrl) {
        this.usuarioOficinaRepository = usuarioOficinaRepository;
        this.trabajadorRepository = trabajadorRepository;
        this.trabajadorResolver = trabajadorResolver;
        this.passwordEncoder = passwordEncoder;
        this.invitacionUsuarioRepository = invitacionUsuarioRepository;
        this.emailService = emailService;
        this.webBaseUrl = webBaseUrl;
    }

    @Transactional(readOnly = true)
    public List<UsuarioOficinaDTO> findAll() {
        return usuarioOficinaRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioOficinaDTO findById(Long id) {
        return toDTO(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UsuarioOficinaDTO> findPendientes() {
        return usuarioOficinaRepository.findByEstadoRegistro(EstadoRegistro.PENDIENTE).stream()
                .map(this::toDTO)
                .toList();
    }

    public UsuarioOficinaDTO crear(CrearUsuarioOficinaRequest req) {
        if (usuarioOficinaRepository.existsByUsername(req.username())) {
            throw new DuplicateResourceException("UsuarioOficina", "username", req.username());
        }

        Rol rolEnum;
        try {
            rolEnum = Rol.valueOf(req.rol().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Rol no válido: " + req.rol()
                    + ". Valores admitidos: ADMIN, GESTOR, CONSULTA.");
        }

        String[] partes = NombreApellidosSplitter.split(req.nombre());
        Trabajador trabajador = trabajadorResolver.resolver(
                partes[0],
                partes[1],
                req.email(),
                req.dni(),
                OrigenTrabajador.OFICINA);
        if (trabajador.getOrigen() == OrigenTrabajador.IMPORTACION) {
            trabajador.setOrigen(OrigenTrabajador.OFICINA);
            trabajador = trabajadorRepository.save(trabajador);
        }

        UsuarioOficina nuevo = UsuarioOficina.builder()
                .username(req.username())
                .password(passwordEncoder.encode(req.password()))
                .email(req.email() != null && !req.email().isBlank() ? req.email() : null)
                .nombreCompleto(req.nombre())
                .dni(req.dni())
                .rol(rolEnum)
                .activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO)
                .trabajador(trabajador)
                .build();

        usuarioOficinaRepository.save(nuevo);
        log.info("Usuario oficina creado por admin: {} (rol: {}) — estado: ACTIVO",
                req.username(), rolEnum);
        return toDTO(nuevo);
    }

    public UsuarioOficinaDTO activar(Long id) {
        UsuarioOficina usuario = findOrThrow(id);

        // Si todavía no tiene Trabajador asociado, lo creamos ahora a partir
        // del nombre completo provisional guardado durante el signup.
        if (usuario.getTrabajador() == null) {
            String[] partes = NombreApellidosSplitter.split(usuario.getNombreCompleto());
            Trabajador trabajador = trabajadorResolver.resolver(
                    partes[0],
                    partes[1],
                    usuario.getEmail(),
                    usuario.getDni(),
                    OrigenTrabajador.OFICINA);
            if (trabajador.getOrigen() == OrigenTrabajador.IMPORTACION) {
                trabajador.setOrigen(OrigenTrabajador.OFICINA);
                trabajador = trabajadorRepository.save(trabajador);
            }
            usuario.setTrabajador(trabajador);
            log.info("Trabajador maestro vinculado al activar usuario oficina {}: id={}",
                    usuario.getUsername(), trabajador.getId());
        }

        usuario.setEstadoRegistro(EstadoRegistro.ACTIVO);
        usuario.setActivo(true);
        usuarioOficinaRepository.save(usuario);
        log.info("Usuario oficina activado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    public UsuarioOficinaDTO rechazar(Long id) {
        UsuarioOficina usuario = findOrThrow(id);
        usuario.setEstadoRegistro(EstadoRegistro.RECHAZADO);
        usuario.setActivo(false);
        usuarioOficinaRepository.save(usuario);
        log.info("Usuario oficina rechazado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    /**
     * Cambia el rol de un usuario de oficina. Reglas:
     *  - No se puede cambiar el rol del propio usuario que ejecuta la operación
     *    (evita lockout accidental si se autobaja de ADMIN).
     *  - No se puede degradar al último ADMIN activo del sistema.
     *  - Solo aplica a usuarios ACTIVOS (no a pendientes/rechazados).
     */
    public UsuarioOficinaDTO cambiarRol(Long id, Rol nuevoRol, String usernameSolicitante) {
        UsuarioOficina usuario = findOrThrow(id);

        if (usuario.getUsername().equalsIgnoreCase(usernameSolicitante)) {
            throw new BusinessException("No puedes cambiar tu propio rol");
        }
        if (usuario.getEstadoRegistro() != EstadoRegistro.ACTIVO || !usuario.isActivo()) {
            throw new BusinessException(
                    "Solo se puede cambiar el rol de usuarios activos");
        }
        if (usuario.getRol() == Rol.ADMIN && nuevoRol != Rol.ADMIN) {
            long adminsActivos = usuarioOficinaRepository.countByRolAndActivoTrue(Rol.ADMIN);
            if (adminsActivos <= 1) {
                throw new BusinessException(
                        "No se puede degradar al último administrador activo del sistema");
            }
        }
        if (usuario.getRol() == nuevoRol) {
            return toDTO(usuario);
        }

        Rol rolAnterior = usuario.getRol();
        usuario.setRol(nuevoRol);
        usuarioOficinaRepository.save(usuario);
        log.info("Cambio de rol: usuario={} ({} → {}) por {}",
                usuario.getUsername(), rolAnterior, nuevoRol, usernameSolicitante);
        return toDTO(usuario);
    }

    public UsuarioOficinaDTO desactivar(Long id) {
        UsuarioOficina usuario = findOrThrow(id);
        usuario.setActivo(false);
        usuarioOficinaRepository.save(usuario);
        log.info("Usuario oficina desactivado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    public UsuarioOficinaDTO editar(Long id, EditarUsuarioOficinaRequest req) {
        UsuarioOficina usuario = findOrThrow(id);
        Trabajador trabajador = usuario.getTrabajador();

        if (req.email() != null && !req.email().isBlank()) {
            String email = req.email().trim();
            usuario.setEmail(email);
            if (trabajador != null) {
                trabajador.setEmail(email);
            }
        }

        if (req.nombre() != null && !req.nombre().isBlank()) {
            String nombre = req.nombre().trim();
            usuario.setNombreCompleto(nombre);
            if (trabajador != null) {
                String[] partes = NombreApellidosSplitter.split(nombre);
                trabajador.setNombre(partes[0]);
                trabajador.setApellidos(partes[1]);
            }
        }

        if (req.dni() != null && !req.dni().isBlank()) {
            String dni = req.dni().trim();
            usuario.setDni(dni);
            if (trabajador != null) {
                trabajador.setDniNie(dni);
            }
        }

        if (trabajador != null) {
            trabajadorRepository.save(trabajador);
        }
        usuarioOficinaRepository.save(usuario);
        log.info("Usuario oficina {} editado por admin", usuario.getUsername());
        return toDTO(usuario);
    }

    public UsuarioOficinaDTO resetearPassword(Long id, String nuevaPassword) {
        UsuarioOficina usuario = findOrThrow(id);
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.setRefreshToken(null);
        usuarioOficinaRepository.save(usuario);
        log.info("Password reset para usuario oficina {}", usuario.getUsername());
        return toDTO(usuario);
    }

    /**
     * Invita a un nuevo administrador por email. Crea el UsuarioOficina en
     * estado inactivo/PENDIENTE (con una password aleatoria inutilizable
     * hasta que acepte la invitacion) y envia el enlace de aceptacion.
     */
    public InvitacionPendienteDTO invitar(InvitarUsuarioRequest req, String usernameInvitador) {
        if (usuarioOficinaRepository.existsByEmailAndActivoTrue(req.email())) {
            throw new BusinessException("Ya existe un usuario activo con este email");
        }

        UsuarioOficina invitador = usuarioOficinaRepository.findByUsername(usernameInvitador)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioOficina", usernameInvitador));

        UsuarioOficina nuevo = UsuarioOficina.builder()
                .username(req.email())
                .password(passwordEncoder.encode(randomToken()))
                .email(req.email())
                .nombreCompleto(req.nombre())
                .rol(Rol.ADMIN)
                .activo(false)
                .estadoRegistro(EstadoRegistro.PENDIENTE)
                .build();
        usuarioOficinaRepository.save(nuevo);

        InvitacionPendienteDTO resultado = crearYEnviarInvitacion(nuevo, invitador, req.nombre(), req.email());
        log.info("Invitación de administrador enviada a {} por {}", req.email(), usernameInvitador);
        return resultado;
    }

    /**
     * Acepta una invitacion con el token en claro recibido por email: activa
     * el usuario asociado con la password elegida y consume la invitacion.
     */
    public void aceptarInvitacion(String tokenEnClaro, AceptarInvitacionRequest req) {
        InvitacionUsuario invitacion = invitacionUsuarioRepository.findByTokenHash(hashSha256(tokenEnClaro))
                .orElseThrow(() -> new BusinessException("Invitación no válida"));

        if (invitacion.getUsedAt() != null) {
            throw new BusinessException("Esta invitación ya ha sido utilizada");
        }
        if (invitacion.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Esta invitación ha caducado");
        }

        UsuarioOficina usuario = invitacion.getUsuario();
        // Defensa en profundidad: aunque los checks de arriba sean correctos,
        // no aceptar si el usuario ya no esta PENDIENTE (p.ej. ya activo).
        if (usuario.getEstadoRegistro() != EstadoRegistro.PENDIENTE) {
            throw new BusinessException("Esta invitación ya no es válida");
        }
        usuario.setPassword(passwordEncoder.encode(req.password()));
        usuario.setActivo(true);
        usuario.setEstadoRegistro(EstadoRegistro.ACTIVO);
        usuarioOficinaRepository.save(usuario);

        invitacion.setUsedAt(LocalDateTime.now());
        invitacionUsuarioRepository.save(invitacion);
        log.info("Invitación aceptada para usuario oficina id={}", usuario.getId());
    }

    /**
     * Invalida la invitacion vigente y envia una nueva con token y
     * expiracion frescos, al mismo usuario e invitador que la original.
     */
    public InvitacionPendienteDTO reenviarInvitacion(Long invitacionId) {
        InvitacionUsuario vieja = findInvitacionOrThrow(invitacionId);
        UsuarioOficina usuario = vieja.getUsuario();

        // No reenviar una invitacion ya aceptada/cancelada: emitiria un
        // token nuevo capaz de reescribir la password de un usuario real.
        if (vieja.getUsedAt() != null || usuario.getEstadoRegistro() != EstadoRegistro.PENDIENTE) {
            throw new BusinessException("No se puede reenviar una invitación ya aceptada o cancelada");
        }

        vieja.setUsedAt(LocalDateTime.now());
        invitacionUsuarioRepository.save(vieja);

        InvitacionPendienteDTO resultado = crearYEnviarInvitacion(
                usuario, vieja.getInvitadoPor(), usuario.getNombreCompleto(), usuario.getEmail());
        log.info("Invitación reenviada para usuario oficina id={}", usuario.getId());
        return resultado;
    }

    /**
     * Invalida una invitacion. Si el usuario invitado nunca llego a
     * aceptarla (sigue inactivo), se borra directamente para liberar su
     * email/username y permitir una nueva invitacion; es seguro porque ese
     * registro nunca tuvo password real ni se uso para nada.
     */
    public void cancelarInvitacion(Long invitacionId) {
        InvitacionUsuario invitacion = findInvitacionOrThrow(invitacionId);
        UsuarioOficina usuario = invitacion.getUsuario();

        // Una invitacion ya usada (aceptada) no se puede "cancelar": borrar
        // su usuario aqui borraria una cuenta real ya activa.
        if (invitacion.getUsedAt() != null) {
            throw new BusinessException("No se puede cancelar una invitación ya utilizada");
        }

        if (usuario.getEstadoRegistro() == EstadoRegistro.PENDIENTE) {
            invitacionUsuarioRepository.deleteByUsuarioId(usuario.getId());
            usuarioOficinaRepository.delete(usuario);
            log.info("Invitación cancelada y usuario pendiente eliminado: id={}, usuarioId={}",
                    invitacionId, usuario.getId());
            return;
        }

        invitacion.setUsedAt(LocalDateTime.now());
        invitacionUsuarioRepository.save(invitacion);
        log.info("Invitación cancelada: id={}", invitacionId);
    }

    @Transactional(readOnly = true)
    public List<InvitacionPendienteDTO> findInvitacionesPendientes() {
        return invitacionUsuarioRepository.findByUsedAtIsNullAndExpiresAtAfter(LocalDateTime.now()).stream()
                .map(this::toInvitacionDTO)
                .toList();
    }

    private InvitacionUsuario findInvitacionOrThrow(Long id) {
        return invitacionUsuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InvitacionUsuario", id));
    }

    private InvitacionPendienteDTO crearYEnviarInvitacion(
            UsuarioOficina usuario, UsuarioOficina invitador, String nombre, String email) {
        String tokenEnClaro = randomToken();
        InvitacionUsuario invitacion = InvitacionUsuario.builder()
                .usuario(usuario)
                .invitadoPor(invitador)
                .tokenHash(hashSha256(tokenEnClaro))
                .expiresAt(LocalDateTime.now().plusHours(HORAS_VALIDEZ_INVITACION))
                .build();
        invitacionUsuarioRepository.save(invitacion);

        String link = webBaseUrl + "/invitacion/" + tokenEnClaro;
        emailService.enviarInvitacionAdmin(email, nombre, link);

        return toInvitacionDTO(invitacion);
    }

    private InvitacionPendienteDTO toInvitacionDTO(InvitacionUsuario invitacion) {
        return new InvitacionPendienteDTO(
                invitacion.getId(),
                invitacion.getUsuario().getNombreCompleto(),
                invitacion.getUsuario().getEmail(),
                invitacion.getInvitadoPor().getUsername(),
                invitacion.getExpiresAt());
    }

    // shortcut: valor aleatorio con UUID+UUID (suficiente entropia, sin libreria nueva).
    private String randomToken() {
        return UUID.randomUUID().toString() + UUID.randomUUID();
    }

    // Duplicado deliberado de AuthOficinaService.hashSha256: extraer a un
    // util compartido si aparece una tercera copia.
    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("Error interno al procesar el token", e);
        }
    }

    private UsuarioOficina findOrThrow(Long id) {
        return usuarioOficinaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioOficina", id));
    }

    private UsuarioOficinaDTO toDTO(UsuarioOficina u) {
        return new UsuarioOficinaDTO(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getNombreCompleto(),
                u.getDni(),
                u.getRol().name(),
                u.isActivo(),
                u.getEstadoRegistro().name(),
                u.getCreadoEn());
    }
}
