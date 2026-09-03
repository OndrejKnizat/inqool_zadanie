package sk.knizat.tennisclub.entity;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests of {@link AuditListener} with a fixed clock (no Spring). */
class AuditListenerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T08:00:00Z");
    private final AuditListener listener = new AuditListener(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void should_stampCreatedAndUpdatedFromClock_when_onCreate() {
        Court court = new Court();

        listener.onCreate(court);

        assertThat(court.getCreatedAt()).isEqualTo(NOW);
        assertThat(court.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_stampOnlyUpdatedFromClock_when_onUpdate() {
        Court court = new Court();
        court.initTimestamps(Instant.parse("2026-01-01T00:00:00Z"));

        listener.onUpdate(court);

        assertThat(court.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(court.getUpdatedAt()).isEqualTo(NOW);
    }
}
