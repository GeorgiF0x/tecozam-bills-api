package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.dto.CrearUsuarioCampoRequest;
import com.tecozam.bills.auth.dto.EditarUsuarioCampoRequest;
import com.tecozam.bills.auth.dto.UsuarioCampoDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
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
public class UsuarioCampoService {

    private final UsuarioCampoRepository usuarioCampoRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorResolver trabajadorResolver;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioCampoDTO> findAll() {
        return usuarioCampoRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioCampoDTO findById(Long id) {
        return toDTO(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UsuarioCampoDTO> findPendientes() {
        return usuarioCampoRepository.findByEstadoRegistro(EstadoRegistro.PENDIENTE).stream()
                .map(this::toDTO)
                .toList();
    }

    public UsuarioCampoDTO crear(CrearUsuarioCampoRequest req) {
        if (usuarioCampoRepository.existsByUsername(req.username())) {
            throw new DuplicateResourceException("UsuarioCampo", "username", req.username());
        }

        Trabajador trabajador = trabajadorResolver.resolver(
                req.nombre(),
                req.apellidos() != null ? req.apellidos() : "",
                null,
                req.dni(),
                OrigenTrabajador.CAMPO);
        // Si reusamos un trabajador previo cuyo origen era IMPORTACION pero ahora
        // se promociona a usuario de campo, marcamos su origen como CAMPO para
        // reflejar que la persona ya tiene cuenta operativa.
        if (trabajador.getOrigen() == OrigenTrabajador.IMPORTACION) {
            trabajador.setOrigen(OrigenTrabajador.CAMPO);
            trabajador = trabajadorRepository.save(trabajador);
        }

        UsuarioCampo nuevo = UsuarioCampo.builder()
                .username(req.username())
                .password(passwordEncoder.encode(req.password()))
                .telefono(req.telefono())
                .nombre(req.nombre())
                .apellidos(req.apellidos())
                .dni(req.dni())
                .activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO)
                .trabajador(trabajador)
                .build();

        usuarioCampoRepository.save(nuevo);
        log.info("Usuario campo creado por admin: {} — estado: ACTIVO", req.username());
        return toDTO(nuevo);
    }

    public UsuarioCampoDTO activar(Long id) {
        UsuarioCampo usuario = findOrThrow(id);

        // Si todavía no tiene Trabajador asociado, lo creamos ahora con los
        // datos provisionales del propio UsuarioCampo (introducidos en signup).
        if (usuario.getTrabajador() == null) {
            Trabajador trabajador = trabajadorResolver.resolver(
                    usuario.getNombre(),
                    usuario.getApellidos() != null ? usuario.getApellidos() : "",
                    null,
                    usuario.getDni(),
                    OrigenTrabajador.CAMPO);
            if (trabajador.getOrigen() == OrigenTrabajador.IMPORTACION) {
                trabajador.setOrigen(OrigenTrabajador.CAMPO);
                trabajador = trabajadorRepository.save(trabajador);
            }
            usuario.setTrabajador(trabajador);
            log.info("Trabajador maestro vinculado al activar usuario campo {}: id={}",
                    usuario.getUsername(), trabajador.getId());
        }

        usuario.setEstadoRegistro(EstadoRegistro.ACTIVO);
        usuario.setActivo(true);
        usuarioCampoRepository.save(usuario);
        log.info("Usuario campo activado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    public UsuarioCampoDTO rechazar(Long id) {
        UsuarioCampo usuario = findOrThrow(id);
        usuario.setEstadoRegistro(EstadoRegistro.RECHAZADO);
        usuario.setActivo(false);
        usuarioCampoRepository.save(usuario);
        log.info("Usuario campo rechazado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    public UsuarioCampoDTO desactivar(Long id) {
        UsuarioCampo usuario = findOrThrow(id);
        usuario.setActivo(false);
        usuarioCampoRepository.save(usuario);
        log.info("Usuario campo desactivado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    public UsuarioCampoDTO editar(Long id, EditarUsuarioCampoRequest req) {
        UsuarioCampo usuario = findOrThrow(id);
        Trabajador trabajador = usuario.getTrabajador();

        if (req.nombre() != null && !req.nombre().isBlank()) {
            String nombre = req.nombre().trim();
            usuario.setNombre(nombre);
            if (trabajador != null) {
                trabajador.setNombre(nombre);
            }
        }

        if (req.apellidos() != null && !req.apellidos().isBlank()) {
            String apellidos = req.apellidos().trim();
            usuario.setApellidos(apellidos);
            if (trabajador != null) {
                trabajador.setApellidos(apellidos);
            }
        }

        if (req.dni() != null && !req.dni().isBlank()) {
            String dni = req.dni().trim();
            usuario.setDni(dni);
            if (trabajador != null) {
                trabajador.setDniNie(dni);
            }
        }

        if (req.telefono() != null && !req.telefono().isBlank()) {
            usuario.setTelefono(req.telefono().trim());
        }

        if (trabajador != null) {
            trabajadorRepository.save(trabajador);
        }
        usuarioCampoRepository.save(usuario);
        log.info("Usuario campo {} editado por admin", usuario.getUsername());
        return toDTO(usuario);
    }

    public UsuarioCampoDTO resetearPassword(Long id, String nuevaPassword) {
        UsuarioCampo usuario = findOrThrow(id);
        usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        usuario.setRefreshToken(null);
        usuarioCampoRepository.save(usuario);
        log.info("Password reset para usuario campo {}", usuario.getUsername());
        return toDTO(usuario);
    }

    private UsuarioCampo findOrThrow(Long id) {
        return usuarioCampoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", id));
    }

    private UsuarioCampoDTO toDTO(UsuarioCampo u) {
        // webauthnEnabled solo es significativo cuando el propio usuario consulta /me;
        // en listados del panel admin no aplica y va siempre a false.
        return new UsuarioCampoDTO(
                u.getId(),
                u.getUsername(),
                u.getTelefono(),
                u.getNombre(),
                u.getApellidos(),
                u.getDni(),
                u.getTrabajador() != null ? u.getTrabajador().getId() : null,
                u.isActivo(),
                u.getEstadoRegistro().name(),
                u.getCreadoEn(),
                false);
    }
}
