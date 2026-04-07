package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.auth.dto.LoginRequest;
import com.tecozam.bills.auth.dto.TokenResponse;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.shared.infrastructure.config.JwtConfig;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
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
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;

    public TokenResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(request.username())
                .orElseThrow(() -> new BusinessException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new BusinessException("Credenciales inválidas");
        }

        UserDetails userDetails = toUserDetails(usuario);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        usuario.setRefreshToken(hashSha256(refreshToken));
        usuarioRepository.save(usuario);

        log.info("Login exitoso para usuario: {}", usuario.getUsername());

        return new TokenResponse(
                accessToken,
                refreshToken,
                usuario.getRol().name(),
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
        Usuario usuario = usuarioRepository.findByUsernameAndActivoTrue(username)
                .orElseThrow(() -> new BusinessException("Usuario no encontrado o inactivo"));

        String hashedToken = hashSha256(refreshToken);
        if (usuario.getRefreshToken() == null || !usuario.getRefreshToken().equals(hashedToken)) {
            throw new BusinessException("Refresh token no reconocido");
        }

        UserDetails userDetails = toUserDetails(usuario);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        usuario.setRefreshToken(hashSha256(newRefreshToken));
        usuarioRepository.save(usuario);

        log.info("Token renovado para usuario: {}", username);

        return new TokenResponse(
                newAccessToken,
                newRefreshToken,
                usuario.getRol().name(),
                usuario.getUsername(),
                jwtConfig.getExpiration());
    }

    public void logout(String username) {
        usuarioRepository.findByUsername(username).ifPresent(usuario -> {
            usuario.setRefreshToken(null);
            usuarioRepository.save(usuario);
            log.info("Logout exitoso para usuario: {}", username);
        });
    }

    private UserDetails toUserDetails(Usuario usuario) {
        String authority = "ROLE_" + usuario.getRol().name();
        return new User(
                usuario.getUsername(),
                usuario.getPassword(),
                List.of(new SimpleGrantedAuthority(authority)));
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
