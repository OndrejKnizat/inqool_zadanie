package sk.knizat.tennisclub.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/**
 * Replaces the application {@link Clock} with a {@link MutableClock}. Import it with {@code @Import} in
 * Spring tests that need deterministic or controllable time; inject it as {@code MutableClock} to steer it.
 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }
}
