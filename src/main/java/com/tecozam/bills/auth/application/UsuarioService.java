package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.auth.dto.CreateUsuarioRequest;
import com.tecozam.bills.auth.dto.UpdateUsuarioRequest;
import com.tecozam.bills.auth.dto.UsuarioDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.shared.domain.enums.Rol;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
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
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioDTO> findAll() {
        return usuarioRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioDTO findById(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
        return toDTO(usuario);
    }

    public UsuarioDTO create(CreateUsuarioRequest request) {
        if (usuarioRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Usuario", "username", request.username());
        }

        Rol rol = parseRol(request.rol());

        Usuario usuario = Usuario.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .rol(rol)
                .activo(true)
                .build();

        if (request.trabajadorId() != null) {
            Trabajador trabajador = trabajadorRepository.findById(request.trabajadorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador", request.trabajadorId()));
            usuario.setTrabajador(trabajador);
        }

        usuario = usuarioRepository.save(usuario);
        log.info("Usuario creado: {} con rol {}", usuario.getUsername(), usuario.getRol());

        return toDTO(usuario);
    }

    public UsuarioDTO update(Long id, UpdateUsuarioRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        if (request.password() != null && !request.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }

        if (request.rol() != null && !request.rol().isBlank()) {
            Rol newRol = parseRol(request.rol());
            usuario.setRol(newRol);
        }

        if (request.trabajadorId() != null) {
            Trabajador trabajador = trabajadorRepository.findById(request.trabajadorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador", request.trabajadorId()));
            usuario.setTrabajador(trabajador);
        }

        usuario = usuarioRepository.save(usuario);
        log.info("Usuario actualizado: {}", usuario.getUsername());

        return toDTO(usuario);
    }

    public void toggleActivo(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));

        if (usuario.isActivo() && usuario.getRol() == Rol.ADMIN) {
            long activeAdmins = usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN);
            if (activeAdmins <= 1) {
                throw new BusinessException(
                        "No se puede desactivar el último administrador activo");
            }
        }

        usuario.setActivo(!usuario.isActivo());
        usuarioRepository.save(usuario);

        log.info("Usuario {} {}: {}",
                usuario.isActivo() ? "activado" : "desactivado",
                usuario.getUsername(), id);
    }

    private Rol parseRol(String rolStr) {
        try {
            return Rol.valueOf(rolStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Rol inválido: " + rolStr + ". Valores permitidos: ADMIN, GESTOR, CONSULTA");
        }
    }

    private UsuarioDTO toDTO(Usuario usuario) {
        return new UsuarioDTO(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getRol().name(),
                usuario.isActivo(),
                usuario.getTrabajador() != null ? usuario.getTrabajador().getId() : null,
                usuario.getTrabajador() != null ? usuario.getTrabajador().getNombre() : null,
                usuario.getCreadoEn());
    }
}
