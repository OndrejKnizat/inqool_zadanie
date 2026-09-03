package sk.knizat.tennisclub.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base of end-to-end API tests: real HTTP stack on a random port, {@code test} profile, controllable clock.
 * The context commits real rows, so every test starts with a reset clock and ends with a cleaned database
 * (FK-safe order), keeping the classes that share this cached context independent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractApiTest {

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    @AfterEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }
}
