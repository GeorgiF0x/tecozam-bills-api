package com.tecozam.bills.tarjeta.infrastructure.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Rate-limit por usuario sobre POST /api/tarjetas/&#123;id&#125;/pin/reveal.
 * 10 intentos por minuto y usuario. Mitiga brute-force sobre el fallback
 * password.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@Slf4j
public class RevealPinRateLimitFilter extends OncePerRequestFilter {

    private static final Pattern URL_PATTERN =
            Pattern.compile("^/api/tarjetas/\\d+/pin/reveal/?$");
    private static final int CAPACITY = 10;
    private static final Duration REFILL = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path == null) path = request.getRequestURI();

        if (!URL_PATTERN.matcher(path).matches()) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String key = auth != null && auth.getName() != null
                ? "user:" + auth.getName()
                : "ip:" + request.getRemoteAddr();

        Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(CAPACITY)
                        .refillIntervally(CAPACITY, REFILL)
                        .build())
                .build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(REFILL.toSeconds()));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Demasiados intentos. Inténtalo más tarde.\",\"retryAfterSeconds\":"
                            + REFILL.toSeconds() + "}");
            log.warn("Rate-limit /pin/reveal bloqueado para {}", key);
        }
    }
}
