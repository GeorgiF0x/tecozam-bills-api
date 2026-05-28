package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
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
public class UsuarioOficinaService {

    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final TrabajadorRepository trabajadorRepository;

    @Transactional(readOnly = true)
    public List<UsuarioOficinaDTO> findAll() {
        return usuarioOficinaRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioOficinaDTO> findPendientes() {
        return usuarioOficinaRepository.findByEstadoRegistro(EstadoRegistro.PENDIENTE).stream()
                .map(this::toDTO)
                .toList();
    }

    public UsuarioOficinaDTO activar(Long id) {
        UsuarioOficina usuario = findOrThrow(id);

        // Si todavía no tiene Trabajador asociado, lo creamos ahora a partir
        // del nombre completo provisional guardado durante el signup.
        if (usuario.getTrabajador() == null) {
            String[] partes = splitNombre(usuario.getNombreCompleto());
            Trabajador trabajador = Trabajador.builder()
                    .nombre(partes[0])
                    .apellidos(partes[1])
                    .email(usuario.getEmail())
                    .activo(true)
                    .build();
            trabajador = trabajadorRepository.save(trabajador);
            usuario.setTrabajador(trabajador);
            log.info("Trabajador maestro creado al activar usuario oficina {}: id={}",
                    usuario.getUsername(), trabajador.getId());
        }

        usuario.setEstadoRegistro(EstadoRegistro.ACTIVO);
        usuario.setActivo(true);
        usuarioOficinaRepository.save(usuario);
        log.info("Usuario oficina activado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    /**
     * Separa un nombre completo en nombre y apellidos.
     * La primera palabra se considera el nombre; el resto, los apellidos.
     * Si la entrada es vacía o nula, devuelve ["", ""].
     */
    private static String[] splitNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return new String[]{"", ""};
        }
        String trim = nombreCompleto.trim().replaceAll("\\s+", " ");
        int idx = trim.indexOf(' ');
        if (idx < 0) {
            return new String[]{trim, ""};
        }
        return new String[]{trim.substring(0, idx), trim.substring(idx + 1)};
    }

    public UsuarioOficinaDTO rechazar(Long id) {
        UsuarioOficina usuario = findOrThrow(id);
        usuario.setEstadoRegistro(EstadoRegistro.RECHAZADO);
        usuario.setActivo(false);
        usuarioOficinaRepository.save(usuario);
        log.info("Usuario oficina rechazado: {} (id={})", usuario.getUsername(), id);
        return toDTO(usuario);
    }

    public UsuarioOficinaDTO desactivar(Long id) {
        UsuarioOficina usuario = findOrThrow(id);
        usuario.setActivo(false);
        usuarioOficinaRepository.save(usuario);
        log.info("Usuario oficina desactivado: {} (id={})", usuario.getUsername(), id);
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
                u.getRol().name(),
                u.isActivo(),
                u.getEstadoRegistro().name(),
                u.getCreadoEn());
    }
}
