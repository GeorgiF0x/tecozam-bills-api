package com.tecozam.bills.shared.infrastructure.security;

import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final UsuarioCampoRepository usuarioCampoRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Buscar primero en usuarios_oficina
        Optional<UsuarioOficina> oficinaOpt = usuarioOficinaRepository.findByUsernameAndActivoTrue(username);
        if (oficinaOpt.isPresent()) {
            UsuarioOficina u = oficinaOpt.get();
            return new User(u.getUsername(), u.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + u.getRol().name())));
        }

        // Buscar en usuarios_campo
        Optional<UsuarioCampo> campoOpt = usuarioCampoRepository.findByUsernameAndActivoTrue(username);
        if (campoOpt.isPresent()) {
            UsuarioCampo u = campoOpt.get();
            return new User(u.getUsername(), u.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_CAMPO")));
        }

        // Fallback: tabla legacy usuarios
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado o inactivo: " + username));

        String authority = "ROLE_" + usuario.getRol().name();
        return new User(usuario.getUsername(), usuario.getPassword(),
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
