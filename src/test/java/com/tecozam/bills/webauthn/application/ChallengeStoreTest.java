package com.tecozam.bills.webauthn.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChallengeStoreTest {

    private final Instant T0 = Instant.parse("2026-06-13T00:00:00Z");

    @Test
    @DisplayName("put → consume devuelve la entry y la borra (single-use)")
    void consumeIsSingleUse() {
        ChallengeStore store = new ChallengeStore(Clock.fixed(T0, ZoneOffset.UTC));
        store.put("tok-1", "payload".getBytes(), 42L);

        Optional<ChallengeStore.Entry> first = store.consume("tok-1");
        assertThat(first).isPresent();
        assertThat(first.get().usuarioCampoId()).isEqualTo(42L);
        assertThat(first.get().payload()).isEqualTo("payload".getBytes());

        Optional<ChallengeStore.Entry> second = store.consume("tok-1");
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("consume devuelve empty si el token nunca se guardó")
    void consumeUnknownTokenIsEmpty() {
        ChallengeStore store = new ChallengeStore(Clock.fixed(T0, ZoneOffset.UTC));
        assertThat(store.consume("ghost")).isEmpty();
    }

    @Test
    @DisplayName("consume tras TTL 5min devuelve empty aunque el token exista")
    void consumeRespectsTtl() {
        TestClock clock = new TestClock(T0);
        ChallengeStore store = new ChallengeStore(clock);
        store.put("tok-1", "payload".getBytes(), 42L);

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        assertThat(store.consume("tok-1")).isEmpty();
    }

    @Test
    @DisplayName("purgeExpired elimina entries con TTL vencido")
    void purgeRemovesExpired() {
        TestClock clock = new TestClock(T0);
        ChallengeStore store = new ChallengeStore(clock);
        store.put("vieja", "x".getBytes(), 1L);

        clock.advance(Duration.ofMinutes(6));
        store.put("nueva", "y".getBytes(), 2L);
        store.purgeExpired();

        // La vieja debe haberse purgado; la nueva sobrevive.
        assertThat(store.consume("vieja")).isEmpty();
        assertThat(store.consume("nueva")).isPresent();
    }

    /** Clock mutable para tests de TTL. */
    private static final class TestClock extends Clock {
        private Instant now;
        TestClock(Instant start) { this.now = start; }
        void advance(Duration d) { this.now = this.now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
    }
}
