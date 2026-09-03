package sk.knizat.tennisclub.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import sk.knizat.tennisclub.support.Fixtures;
import sk.knizat.tennisclub.support.MutableClock;
import sk.knizat.tennisclub.support.TestClockConfig;

/**
 * Shared base of DAO integration tests: Spring context on H2 with the Liquibase schema, a controllable
 * {@link MutableClock} and one rolled-back transaction per test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@Transactional
abstract class AbstractDaoTest {

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    protected MutableClock clock;

    protected Fixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        clock.reset();
        fixtures = new Fixtures(em);
    }
}
