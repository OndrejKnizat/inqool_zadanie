package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the data initialisation switch turned off (the {@code test} profile default): the
 * {@link DataInitializer} bean must not exist. Shares the cached default test context.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataInitializerDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void should_notRegisterInitializerBean_when_dataInitDisabled() {
        assertThat(context.getBeanNamesForType(DataInitializer.class)).isEmpty();
    }
}
