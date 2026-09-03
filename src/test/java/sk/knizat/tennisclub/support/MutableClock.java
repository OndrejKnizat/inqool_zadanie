package sk.knizat.tennisclub.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Test {@link Clock} whose instant can be set or advanced explicitly, making audit timestamps deterministic.
 */
public class MutableClock extends Clock {

    public static final Instant DEFAULT_NOW = Instant.parse("2026-06-01T10:00:00Z");

    private volatile Instant now;
    private final ZoneId zone;

    public MutableClock() {
        this(DEFAULT_NOW, ZoneOffset.UTC);
    }

    public MutableClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.now = instant;
    }

    public Instant advance(Duration duration) {
        now = now.plus(duration);
        return now;
    }

    /** Resets the clock to {@link #DEFAULT_NOW}; call it in {@code @BeforeEach} of tests sharing the context. */
    public void reset() {
        now = DEFAULT_NOW;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
