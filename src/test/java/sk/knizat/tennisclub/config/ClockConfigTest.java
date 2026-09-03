package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void should_returnUtcClock_when_beanCreated() {
        Clock clock = new ClockConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
