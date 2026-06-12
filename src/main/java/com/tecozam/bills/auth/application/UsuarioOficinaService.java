package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.dto.CrearUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.EditarUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import com.tecozam.bills.shared.util.NombreApellidosSplitter;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.trabajador.application.TrabajadorResolver;
import com.tecozam.bills.trabajador.domain.OrigenTrabajador;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UsuarioOficinaService {

    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorResolver trabajadorResolver;
    private final PasswordEncoder passwordEncoder;

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
