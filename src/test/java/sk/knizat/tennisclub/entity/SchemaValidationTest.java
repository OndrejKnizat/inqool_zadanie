package sk.knizat.tennisclub.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that Liquibase created the schema on H2 and that Hibernate ({@code ddl-auto=validate}) accepts it,
 * then persists and reads back one full object graph. Runs inside a rolled-back transaction, so the shared
 * in-memory database stays clean for other test classes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaValidationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager em;

    @Test
    void should_haveAllFourTables_when_liquibaseApplied() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class);

        assertThat(tables).map(String::toLowerCase)
                .contains("surface_type", "court", "app_user", "reservation");
    }

    @Test
    void should_haveIndexes_when_liquibaseApplied() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class);

        assertThat(indexes).map(String::toLowerCase)
                .contains("idx_reservation_court_time", "idx_app_user_phone", "idx_court_number");
    }

    @Test
    void should_persistAndReadBackObjectGraph_when_listenerFillsTimestamps() {
        Instant start = Instant.parse("2026-06-01T10:00:00Z");
        Instant end = Instant.parse("2026-06-01T11:30:00Z");

        SurfaceType surface = new SurfaceType();
        surface.setName("Clay");
        surface.setPricePerMinute(new BigDecimal("2.50"));
        em.persist(surface);

        Court court = new Court();
        court.setCourtNumber(1);
        court.setName("Centre court");
        court.setSurfaceType(surface);
        em.persist(court);

        User user = new User();
        user.setPhoneNumber("+421900000001");
        user.setName("Jane Doe");
        em.persist(user);

        Reservation reservation = new Reservation();
        reservation.setCourt(court);
        reservation.setUser(user);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setGameType(GameType.DOUBLES);
        reservation.setPrice(new BigDecimal("337.50"));
        em.persist(reservation);

        em.flush();
        em.clear();

        Reservation found = em.find(Reservation.class, reservation.getId());

        assertThat(found).isNotNull().isNotSameAs(reservation);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isEqualTo(found.getCreatedAt());
        assertThat(found.isDeleted()).isFalse();
        assertThat(found.getDeletedAt()).isNull();
        assertThat(found.getStartTime()).isEqualTo(start);
        assertThat(found.getEndTime()).isEqualTo(end);
        assertThat(found.getGameType()).isEqualTo(GameType.DOUBLES);
        assertThat(found.getPrice()).isEqualByComparingTo("337.50");
        assertThat(found.getUser().getPhoneNumber()).isEqualTo("+421900000001");
        assertThat(found.getUser().getRole()).isEqualTo(Role.USER);
        assertThat(found.getUser().getPasswordHash()).isNull();
        assertThat(found.getCourt().getCourtNumber()).isEqualTo(1);
        assertThat(found.getCourt().getSurfaceType().getName()).isEqualTo("Clay");
        assertThat(found.getCourt().getSurfaceType().getPricePerMinute()).isEqualByComparingTo("2.50");
        assertThat(found.getCourt().getCreatedAt()).isNotNull();
    }

    @Test
    void should_treatLazyProxyAndLoadedEntityAsEqual_when_sameId() {
        SurfaceType surface = new SurfaceType();
        surface.setName("Grass");
        surface.setPricePerMinute(new BigDecimal("3.00"));
        em.persist(surface);
        em.flush();
        em.clear();

        SurfaceType proxy = em.getReference(SurfaceType.class, surface.getId());
        SurfaceType detached = new SurfaceType();
        detached.setId(surface.getId());

        assertThat(proxy.getClass()).isNotEqualTo(SurfaceType.class);
        assertThat(detached).isEqualTo(proxy);
        assertThat(proxy).isEqualTo(detached);
        assertThat(proxy).hasSameHashCodeAs(detached);
        assertThat(proxy).hasToString("SurfaceType{id=" + surface.getId() + "}");
    }

    @Test
    void should_refreshUpdatedAt_when_entityIsModified() {
        SurfaceType surface = new SurfaceType();
        surface.setName("Hard");
        surface.setPricePerMinute(new BigDecimal("1.00"));
        em.persist(surface);
        em.flush();
        Instant created = surface.getCreatedAt();

        surface.setName("Hard court");
        em.flush();

        assertThat(surface.getCreatedAt()).isEqualTo(created);
        assertThat(surface.getUpdatedAt()).isAfterOrEqualTo(created);
    }
}
