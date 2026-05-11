package com.tecozam.bills.auth.infrastructure.migration;

import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Migra los datos de la tabla legacy {@code usuarios} a las nuevas tablas
 * {@code usuarios_oficina} y {@code usuarios_campo} si estas están vacías.
 * <p>
 * Se ejecuta una sola vez al arrancar: una vez que las tablas nuevas tengan datos,
 * la migración es un no-op.
 * </p>
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class UsuarioMigrationRunner implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final UsuarioCampoRepository usuarioCampoRepository;

    @Override
    @Transactional
    public void run(String... args) {
        migrateOficina();
        migrateCampo();
    }

    private void migrateOficina() {
        if (usuarioOficinaRepository.count() > 0) {
            log.info("Migración omitida: usuarios_oficina ya contiene datos.");
            return;
        }

        List<Usuario> adminsYGestores = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.ADMIN || u.getRol() == Rol.GESTOR)
                .toList();

        if (adminsYGestores.isEmpty()) {
            log.info("Migración: no hay usuarios ADMIN/GESTOR en la tabla legacy.");
            return;
        }

        for (Usuario u : adminsYGestores) {
            UsuarioOficina oficina = UsuarioOficina.builder()
                    .username(u.getUsername())
                    .password(u.getPassword())
                    .rol(u.getRol())
                    .activo(u.isActivo())
                    .estadoRegistro(EstadoRegistro.ACTIVO)
                    .build();
            usuarioOficinaRepository.save(oficina);
            log.info("Migrado a usuarios_oficina: {} (rol={})", u.getUsername(), u.getRol());
        }
        log.info("Migración usuarios_oficina completada: {} usuarios migrados.", adminsYGestores.size());
    }

    private void migrateCampo() {
        if (usuarioCampoRepository.count() > 0) {
            log.info("Migración omitida: usuarios_campo ya contiene datos.");
            return;
        }

        List<Usuario> consultas = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.CONSULTA)
                .toList();

        if (consultas.isEmpty()) {
            log.info("Migración: no hay usuarios CONSULTA en la tabla legacy.");
            return;
        }

        for (Usuario u : consultas) {
            UsuarioCampo campo = UsuarioCampo.builder()
                    .username(u.getUsername())
                    .password(u.getPassword())
                    .trabajador(u.getTrabajador())
                    .activo(u.isActivo())
                    .estadoRegistro(EstadoRegistro.ACTIVO)
                    .build();

            if (u.getTrabajador() != null) {
                campo.setNombre(u.getTrabajador().getNombre());
                campo.setApellidos(u.getTrabajador().getApellidos());
            }

            usuarioCampoRepository.save(campo);
            log.info("Migrado a usuarios_campo: {} (trabajador_id={})",
                    u.getUsername(),
                    u.getTrabajador() != null ? u.getTrabajador().getId() : "null");
        }
        log.info("Migración usuarios_campo completada: {} usuarios migrados.", consultas.size());
    }
}
