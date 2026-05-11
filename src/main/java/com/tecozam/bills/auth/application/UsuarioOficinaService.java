package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
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
