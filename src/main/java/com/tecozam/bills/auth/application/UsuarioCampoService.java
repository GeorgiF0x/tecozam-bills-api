package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.dto.UsuarioCampoDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional(readOnly = true)
    public List<UsuarioCampoDTO> findAll() {
        return usuarioCampoRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioCampoDTO> findPendientes() {
        return usuarioCampoRepository.findByEstadoRegistro(EstadoRegistro.PENDIENTE).stream()
                .map(this::toDTO)
                .toList();
    }

    public UsuarioCampoDTO activar(Long id) {
        UsuarioCampo usuario = findOrThrow(id);

        // Si todavía no tiene Trabajador asociado, lo creamos ahora con los
        // datos provisionales del propio UsuarioCampo (introducidos en signup).
        if (usuario.getTrabajador() == null) {
            Trabajador trabajador = Trabajador.builder()
                    .nombre(usuario.getNombre())
                    .apellidos(usuario.getApellidos())
                    .activo(true)
                    .build();
            if (usuario.getDni() != null && !usuario.getDni().isBlank()) {
                trabajador.setDniNie(usuario.getDni());
            }
            trabajador = trabajadorRepository.save(trabajador);
            usuario.setTrabajador(trabajador);
            log.info("Trabajador maestro creado al activar usuario campo {}: id={}",
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

    private UsuarioCampo findOrThrow(Long id) {
        return usuarioCampoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", id));
    }

    private UsuarioCampoDTO toDTO(UsuarioCampo u) {
        return new UsuarioCampoDTO(
                u.getId(),
                u.getUsername(),
                u.getTelefono(),
                u.getNombre(),
                u.getApellidos(),
                u.getTrabajador() != null ? u.getTrabajador().getId() : null,
                u.isActivo(),
                u.getEstadoRegistro().name(),
                u.getCreadoEn());
    }
}
