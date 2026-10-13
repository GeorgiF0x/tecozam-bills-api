package com.tecozam.bills.shared.infrastructure.security;

import com.tecozam.bills.shared.infrastructure.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;

    /**
     * Claim que distingue un access token de un refresh token. Sin esto, un
     * refresh token robado (vida larga, hasta 7 dias) se podia usar tal cual
     * como access token contra cualquier endpoint protegido, sin pasar por
     * /refresh — confirmado explotable en vivo. Un token sin este claim (los
     * ya emitidos antes de este fix) se trata como no valido para autenticar.
     */
    private static final String TOKEN_USE_CLAIM = "token_use";
    public static final String TOKEN_USE_ACCESS = "access";
    private static final String TOKEN_USE_REFRESH = "refresh";

    public String generateAccessToken(UserDetails userDetails) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_CONSULTA");

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("rol", role)
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateAccessToken(UserDetails userDetails, String tipo) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_CONSULTA");

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("rol", role)
                .claim("tipo", tipo)
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UserDetails userDetails, String tipo) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("tipo", tipo)
                .claim(TOKEN_USE_CLAIM, TOKEN_USE_REFRESH)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtConfig.getRefreshExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return extractClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return extractClaims(token).get("rol", String.class);
    }

    public String getTipoFromToken(String token) {
        return extractClaims(token).get("tipo", String.class);
    }

    public String getTokenUseFromToken(String token) {
        return extractClaims(token).get(TOKEN_USE_CLAIM, String.class);
    }

    public boolean validateToken(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token JWT expirado: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Token JWT inválido: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Token JWT vacío o nulo: {}", e.getMessage());
        }
        return false;
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
