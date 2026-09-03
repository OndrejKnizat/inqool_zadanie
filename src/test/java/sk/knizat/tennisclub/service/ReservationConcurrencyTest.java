package sk.knizat.tennisclub.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import sk.knizat.tennisclub.dao.CourtDao;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.support.DatabaseCleaner;
import sk.knizat.tennisclub.support.MutableClock;
import sk.knizat.tennisclub.support.TestClockConfig;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the court row lock (ARCHITECTURE.md O-7): two transactions creating the same slot on the same
 * court at the same time must never both succeed. The second one waits for the lock, then sees the
 * committed reservation and fails the overlap check. When the lock is held longer than the database lock
 * timeout ({@code LOCK_TIMEOUT} in the test JDBC URL), the waiting request fails with a 409 conflict.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class ReservationConcurrencyTest {

    private static final int THREADS = 2;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private CourtService courtService;

    @Autowired
    private SurfaceTypeService surfaceTypeService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CourtDao courtDao;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long courtId;

    @BeforeEach
    void setUp() {
        clock.reset();
        Long surfaceId = surfaceTypeService.create(new SurfaceTypeRequest("Clay", new BigDecimal("2.00"))).id();
        courtId = courtService.create(new CourtRequest(1, "Centre court", surfaceId)).id();
    }

    @AfterEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void should_createExactlyOneReservation_when_twoThreadsBookSameSlotConcurrently() throws Exception {
        Instant start = MutableClock.DEFAULT_NOW.plus(Duration.ofDays(1));
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<Object>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                String phone = "+42190000000" + i;
                Callable<Object> attempt = () -> {
                    ready.countDown();
                    assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
                    try {
                        return reservationService.create(new CreateReservationRequest(1, start,
                                start.plus(Duration.ofHours(1)), GameTypeDto.SINGLES, phone, "Racer " + phone));
                    } catch (RuntimeException ex) {
                        return ex;
                    }
                };
                futures.add(executor.submit(attempt));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes).filteredOn(ReservationResponse.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ValidationException.class::isInstance).hasSize(1)
                    .first().asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ValidationException.class))
                    .extracting(Throwable::getMessage)
                    .isEqualTo("Reservation overlaps with an existing reservation on court 1");
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class)).isEqualTo(1);
        } catch (ExecutionException ex) {
            throw new AssertionError("Reservation attempt failed unexpectedly", ex.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_failWithConflict_when_courtLockIsHeldLongerThanLockTimeout() throws Exception {
        Instant start = MutableClock.DEFAULT_NOW.plus(Duration.ofDays(1));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.execute(status -> {
                courtDao.findByIdForUpdate(courtId);
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            // shorten the lock timeout on the waiting session only (H2: SET LOCK_TIMEOUT is per session)
            assertThatThrownBy(() -> transactionTemplate.execute(status -> {
                jdbcTemplate.execute("SET LOCK_TIMEOUT 300");
                return reservationService.create(new CreateReservationRequest(1, start,
                        start.plus(Duration.ofHours(1)), GameTypeDto.SINGLES, "+421900000009", "Waiter"));
            }))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Court with number 1 is locked by a concurrent reservation, please retry");

            release.countDown();
            holder.get(30, TimeUnit.SECONDS);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation", Integer.class)).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
