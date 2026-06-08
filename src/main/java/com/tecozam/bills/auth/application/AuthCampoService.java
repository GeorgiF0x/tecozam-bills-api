package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.dto.LoginRequest;
import com.tecozam.bills.auth.dto.TokenResponse;
import com.tecozam.bills.auth.dto.UsuarioCampoDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.infrastructure.config.JwtConfig;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
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
public class AuthCampoService {

    private static final String TIPO = "CAMPO";
    // Rol sintético para usuarios de campo — no tienen Rol en la tabla, usamos CONSULTA como legado
    private static final String CAMPO_AUTHORITY = "ROLE_CAMPO";

    private final UsuarioCampoRepository usuarioCampoRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    public TokenResponse login(LoginRequest request) {
        UsuarioCampo usuario = usuarioCampoRepository.findByUsername(request.username())
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
        usuarioCampoRepository.save(usuario);

        log.info("Login campo exitoso para usuario: {}", usuario.getUsername());

        return new TokenResponse(
                accessToken,
                refreshToken,
                CAMPO_AUTHORITY,
                usuario.getUsername(),
                jwtConfig.getExpiration());
    }

    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("Refresh token requerido");
        }
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("Refresh token inválido o expirado");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        UsuarioCampo usuario = usuarioCampoRepository.findByUsernameAndActivoTrue(username)
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
        usuarioCampoRepository.save(usuario);

        log.info("Token campo renovado para usuario: {}", username);

        return new TokenResponse(
                newAccessToken,
                newRefreshToken,
                CAMPO_AUTHORITY,
                usuario.getUsername(),
                jwtConfig.getExpiration());
    }

    @Transactional(readOnly = true)
    public UsuarioCampoDTO me(String username) {
        UsuarioCampo usuario = usuarioCampoRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", username));
        return toDTO(usuario);
    }

    public void logout(String username) {
        usuarioCampoRepository.findByUsername(username).ifPresent(u -> {
            u.setRefreshToken(null);
            usuarioCampoRepository.save(u);
            log.info("Logout campo para usuario: {}", username);
        });
    }

    private UserDetails toUserDetails(UsuarioCampo usuario) {
        return new User(
                usuario.getUsername(),
                usuario.getPassword(),
                List.of(new SimpleGrantedAuthority(CAMPO_AUTHORITY)));
    }

    private UsuarioCampoDTO toDTO(UsuarioCampo u) {
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
