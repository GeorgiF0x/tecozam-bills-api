package com.tecozam.bills.webauthn.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché in-memory de challenges WebAuthn (registro y assertion). Single-use
 * con TTL de 5 minutos. Token → payload (challenge serializado por el
 * servicio caller) + usuario al que pertenece.
 *
 * In-memory porque la API corre single-replica en Dokploy. Si se escala a
 * multi-instance, mover a Redis (ver design Open Questions).
 */
@Component
@Slf4j
public class ChallengeStore {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public ChallengeStore() {
        this(Clock.systemUTC());
    }

    public ChallengeStore(Clock clock) {
        this.clock = clock;
    }

    public void put(String token, byte[] payload, Long usuarioCampoId) {
        entries.put(token, new Entry(payload, usuarioCampoId, clock.instant()));
    }

    public Optional<Entry> consume(String token) {
        Entry e = entries.remove(token);
        if (e == null) return Optional.empty();
        if (clock.instant().isAfter(e.createdAt().plus(TTL))) return Optional.empty();
        return Optional.of(e);
    }

    /** Limpieza periódica de challenges expirados. */
    @Scheduled(fixedDelayString = "PT1M")
    public void purgeExpired() {
        Instant cutoff = clock.instant().minus(TTL);
        int before = entries.size();
        entries.values().removeIf(e -> e.createdAt().isBefore(cutoff));
        int removed = before - entries.size();
        if (removed > 0) {
            log.debug("ChallengeStore purgeExpired: removed {} entries", removed);
        }
    }

    public record Entry(byte[] payload, Long usuarioCampoId, Instant createdAt) {}
}
