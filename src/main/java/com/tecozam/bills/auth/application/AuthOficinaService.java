package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.dto.LoginRequest;
import com.tecozam.bills.auth.dto.RegistroOficinaRequest;
import com.tecozam.bills.auth.dto.RegistroResponse;
import com.tecozam.bills.auth.dto.TokenResponse;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import com.tecozam.bills.shared.infrastructure.config.JwtConfig;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.shared.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthOficinaService {

    private static final String TIPO = "OFICINA";

    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    public TokenResponse login(LoginRequest request) {
        UsuarioOficina usuario = usuarioOficinaRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("Credenciales inválidas"));

        if (!usuario.isActivo()) {
            throw new BusinessException("Credenciales inválidas");
        }

        if (usuario.getEstadoRegistro() != EstadoRegistro.ACTIVO) {
            throw new BusinessException("Tu cuenta está pendiente de activación o ha sido rechazada");
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new BusinessException("Credenciales inválidas");
        }

        UserDetails userDetails = toUserDetails(usuario);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails, TIPO);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails, TIPO);

        usuario.setRefreshToken(hashSha256(refreshToken));
        usuarioOficinaRepository.save(usuario);

        log.info("Login oficina exitoso para usuario: {}", usuario.getUsername());

        return new TokenResponse(
                accessToken,
                refreshToken,
                usuario.getRol().name(),
                usuario.getUsername(),
                jwtConfig.getExpiration());
    }

    public RegistroResponse registro(RegistroOficinaRequest request) {
        if (usuarioOficinaRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("UsuarioOficina", "username", request.username());
        }

        UsuarioOficina nuevo = UsuarioOficina.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .email(request.email())
                .nombreCompleto(request.nombre())
                .dni(request.dni())
                .rol(Rol.GESTOR)
                .activo(true)
                .estadoRegistro(EstadoRegistro.PENDIENTE)
                .build();

        usuarioOficinaRepository.save(nuevo);
        log.info("Registro de usuario oficina: {} — estado: PENDIENTE", request.username());

        return new RegistroResponse("PENDIENTE", "Tu cuenta está pendiente de activación");
    }

    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Refresh token requerido");
        }
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("Refresh token inválido o expirado");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        UsuarioOficina usuario = usuarioOficinaRepository.findByUsernameAndActivoTrue(username)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado o inactivo"));

        if (usuario.getEstadoRegistro() != EstadoRegistro.ACTIVO) {
            throw new BusinessException("La cuenta no está activa");
        }

        String hashedToken = hashSha256(refreshToken);
        if (usuario.getRefreshToken() == null || !usuario.getRefreshToken().equals(hashedToken)) {
            throw new BusinessException("Refresh token no reconocido");
        }

        UserDetails userDetails = toUserDetails(usuario);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails, TIPO);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails, TIPO);

        usuario.setRefreshToken(hashSha256(newRefreshToken));
        usuarioOficinaRepository.save(usuario);

        log.info("Token oficina renovado para usuario: {}", username);

        return new TokenResponse(
                newAccessToken,
                newRefreshToken,
                usuario.getRol().name(),
                usuario.getUsername(),
                jwtConfig.getExpiration());
    }

    @Transactional(readOnly = true)
    public UsuarioOficinaDTO me(String username) {
        UsuarioOficina usuario = usuarioOficinaRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioOficina", username));
        return toDTO(usuario);
    }

    public void logout(String username) {
        usuarioOficinaRepository.findByUsername(username).ifPresent(u -> {
            u.setRefreshToken(null);
            usuarioOficinaRepository.save(u);
            log.info("Logout oficina para usuario: {}", username);
        });
    }

    private UserDetails toUserDetails(UsuarioOficina usuario) {
        return new User(
                usuario.getUsername(),
                usuario.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
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

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException("Error interno al procesar el token", e);
        }
    }
}
