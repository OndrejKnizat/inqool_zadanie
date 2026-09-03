package sk.knizat.tennisclub.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.Reservation;
import sk.knizat.tennisclub.entity.User;
import sk.knizat.tennisclub.support.Fixtures;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static sk.knizat.tennisclub.support.Fixtures.T10;
import static sk.knizat.tennisclub.support.Fixtures.T11;
import static sk.knizat.tennisclub.support.Fixtures.T12;
import static sk.knizat.tennisclub.support.Fixtures.T13;

/** Integration tests of {@link ReservationDao}. */
class ReservationDaoTest extends AbstractDaoTest {

    @Autowired
    private ReservationDao reservationDao;

    private Court court1;
    private Court court2;
    private User alice;
    private User bob;

    @BeforeEach
    void setUpGraph() {
        court1 = fixtures.persistCourt(1);
        court2 = fixtures.persistCourt(2);
        alice = fixtures.persistUser("+421900000001");
        bob = fixtures.persistUser("+421900000002");
    }

    // --- findByCourtNumberOrderByCreatedAt ---

    @Test
    void should_orderByCreatedAt_when_findByCourtNumber() {
        // persisted in reverse start-time order with an advancing clock, so createdAt != startTime ordering
        Reservation createdFirst = fixtures.persistReservation(court1, alice, T12, T13);
        clock.advance(Duration.ofMinutes(5));
        Reservation createdSecond = fixtures.persistReservation(court1, bob, T10, T11);
        clock.advance(Duration.ofMinutes(5));
        Reservation createdThird = fixtures.persistReservation(court1, alice, T11, T12);
        fixtures.persistReservation(court2, alice, T10, T11);
        fixtures.flushAndClear();

        List<Reservation> result = reservationDao.findByCourtNumberOrderByCreatedAt(1);

        assertThat(result).extracting(Reservation::getId)
                .containsExactly(createdFirst.getId(), createdSecond.getId(), createdThird.getId());
        assertThat(result).extracting(Reservation::getCreatedAt).isSorted();
    }

    @Test
    void should_ignoreDeletedReservationButIncludeDeletedCourt_when_findByCourtNumber() {
        Reservation kept = fixtures.persistReservation(court1, alice, T10, T11);
        fixtures.persistDeleted(Fixtures.reservation(court1, alice, T11, T12));
        court1.markDeleted(clock.instant());
        fixtures.flushAndClear();

        List<Reservation> result = reservationDao.findByCourtNumberOrderByCreatedAt(1);

        assertThat(result).extracting(Reservation::getId).containsExactly(kept.getId());
        assertThat(result.get(0).getCourt().isDeleted()).isTrue();
        assertThat(reservationDao.findByCourtNumberOrderByCreatedAt(99)).isEmpty();
    }

    @Test
    void should_fetchWholeGraph_when_findByCourtNumber() {
        fixtures.persistReservation(court1, alice, T10, T11);
        fixtures.flushAndClear();

        Reservation loaded = reservationDao.findByCourtNumberOrderByCreatedAt(1).get(0);
        em.detach(loaded);

        assertThat(loaded.getCourt().getCourtNumber()).isEqualTo(1);
        assertThat(loaded.getCourt().getSurfaceType().getName()).isEqualTo("Surface of court 1");
        assertThat(loaded.getUser().getPhoneNumber()).isEqualTo("+421900000001");
    }

    // --- findByPhoneNumber ---

    @Test
    void should_returnAllOrderedByStart_when_findByPhoneNumberWithoutFutureFilter() {
        Reservation later = fixtures.persistReservation(court1, alice, T12, T13);
        Reservation earlier = fixtures.persistReservation(court2, alice, T10, T11);
        fixtures.persistReservation(court1, bob, T10, T11);
        fixtures.persistDeleted(Fixtures.reservation(court1, alice, T11, T12));
        fixtures.flushAndClear();

        List<Reservation> result = reservationDao.findByPhoneNumber("+421900000001", false, null);

        assertThat(result).extracting(Reservation::getId).containsExactly(earlier.getId(), later.getId());
        assertThat(result.get(0).getUser().getName()).isEqualTo("User +421900000001");
    }

    @Test
    void should_returnOnlyStrictlyFuture_when_findByPhoneNumberFutureOnly() {
        fixtures.persistReservation(court1, alice, T10, T11);           // past
        fixtures.persistReservation(court1, alice, T11, T13);           // ongoing at now
        Reservation startsAtNow = fixtures.persistReservation(court2, alice, T12, T13);
        Reservation future = fixtures.persistReservation(court1, alice, T13, T13.plus(Duration.ofHours(1)));
        fixtures.flushAndClear();
        Instant now = T12;

        assertThat(reservationDao.findByPhoneNumber("+421900000001", true, now))
                .extracting(Reservation::getId).containsExactly(future.getId());
        assertThat(reservationDao.findByPhoneNumber("+421900000001", true, T11.plus(Duration.ofMinutes(30))))
                .extracting(Reservation::getId).containsExactly(startsAtNow.getId(), future.getId());
    }

    @Test
    void should_returnEmpty_when_findByPhoneNumberOfDeletedOrUnknownUser() {
        fixtures.persistReservation(court1, alice, T10, T11);
        alice.markDeleted(clock.instant());
        fixtures.flushAndClear();

        assertThat(reservationDao.findByPhoneNumber("+421900000001", false, null)).isEmpty();
        assertThat(reservationDao.findByPhoneNumber("+421900000999", false, null)).isEmpty();
    }

    // --- findAllOrderByStartTime / generic ---

    @Test
    void should_orderByStartTimeAcrossCourts_when_findAllOrderByStartTime() {
        Reservation third = fixtures.persistReservation(court1, alice, T12, T13);
        Reservation first = fixtures.persistReservation(court2, bob, T10, T11);
        Reservation second = fixtures.persistReservation(court1, bob, T11, T12);
        fixtures.persistDeleted(Fixtures.reservation(court2, alice, T10, T11));
        fixtures.flushAndClear();

        assertThat(reservationDao.findAllOrderByStartTime()).extracting(Reservation::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        assertThat(reservationDao.findAll()).hasSize(3);
    }

    @Test
    void should_hideFromAllReads_when_reservationSoftDeleted() {
        Reservation reservation = reservationDao.save(Fixtures.reservation(court1, alice, T10, T11));
        fixtures.flushAndClear();
        assertThat(reservationDao.findById(reservation.getId())).isPresent();

        reservationDao.softDelete(reservationDao.findById(reservation.getId()).orElseThrow(), clock.instant());
        fixtures.flushAndClear();

        assertThat(reservationDao.findById(reservation.getId())).isEmpty();
        assertThat(reservationDao.existsById(reservation.getId())).isFalse();
        assertThat(reservationDao.findAllOrderByStartTime()).isEmpty();
        assertThat(reservationDao.existsOverlapping(court1.getId(), T10, T11, null)).isFalse();
        assertThat(reservationDao.existsUnfinishedByCourt(court1.getId(), Instant.EPOCH)).isFalse();
        assertThat(reservationDao.existsUnfinishedByUser(alice.getId(), Instant.EPOCH)).isFalse();
    }

    // --- existsOverlapping ---

    @Test
    void should_detectOverlap_when_intervalsIntersect() {
        Reservation existing = fixtures.persistReservation(court1, alice, T11, T12);
        fixtures.flushAndClear();
        Long courtId = court1.getId();
        Instant t1130 = T11.plus(Duration.ofMinutes(30));
        Instant t1230 = T12.plus(Duration.ofMinutes(30));

        assertThat(reservationDao.existsOverlapping(courtId, T10, t1130, null)).as("overlaps start").isTrue();
        assertThat(reservationDao.existsOverlapping(courtId, t1130, T13, null)).as("overlaps end").isTrue();
        assertThat(reservationDao.existsOverlapping(courtId, T10, T13, null)).as("contains existing").isTrue();
        assertThat(reservationDao.existsOverlapping(courtId, t1130, t1130.plus(Duration.ofMinutes(10)), null))
                .as("contained in existing").isTrue();
        assertThat(reservationDao.existsOverlapping(courtId, T11, T12, null)).as("same interval").isTrue();
        assertThat(reservationDao.existsOverlapping(courtId, T12, t1230, null))
                .as("not overlapping, no exclude id").isFalse();
    }

    @Test
    void should_notDetectOverlap_when_intervalsOnlyTouch() {
        fixtures.persistReservation(court1, alice, T11, T12);
        fixtures.flushAndClear();
        Long courtId = court1.getId();

        assertThat(reservationDao.existsOverlapping(courtId, T10, T11, null)).as("ends when existing starts").isFalse();
        assertThat(reservationDao.existsOverlapping(courtId, T12, T13, null)).as("starts when existing ends").isFalse();
        assertThat(reservationDao.existsOverlapping(courtId, T13, T13.plus(Duration.ofHours(1)), null)).isFalse();
    }

    @Test
    void should_ignoreOwnReservation_when_excludeIdGiven() {
        Reservation own = fixtures.persistReservation(court1, alice, T11, T12);
        Reservation other = fixtures.persistReservation(court1, bob, T12, T13);
        fixtures.flushAndClear();
        Long courtId = court1.getId();

        assertThat(reservationDao.existsOverlapping(courtId, T11, T12, own.getId())).isFalse();
        assertThat(reservationDao.existsOverlapping(courtId, T11, T12, other.getId())).isTrue();
        assertThat(reservationDao.existsOverlapping(courtId, T11, T12.plus(Duration.ofMinutes(1)), own.getId()))
                .as("still collides with the other reservation").isTrue();
    }

    @Test
    void should_ignoreDeletedAndOtherCourt_when_existsOverlapping() {
        fixtures.persistDeleted(Fixtures.reservation(court1, alice, T11, T12));
        fixtures.persistReservation(court2, alice, T11, T12);
        fixtures.flushAndClear();

        assertThat(reservationDao.existsOverlapping(court1.getId(), T11, T12, null)).isFalse();
        assertThat(reservationDao.existsOverlapping(court2.getId(), T11, T12, null)).isTrue();
    }

    // --- existsUnfinishedByCourt / existsUnfinishedByUser ---

    @Test
    void should_detectUnfinishedReservations_when_existsUnfinishedByCourt() {
        fixtures.persistReservation(court1, alice, T11, T12);
        fixtures.persistDeleted(Fixtures.reservation(court2, alice, T12, T13));
        fixtures.flushAndClear();

        assertThat(reservationDao.existsUnfinishedByCourt(court1.getId(), T10)).isTrue();
        assertThat(reservationDao.existsUnfinishedByCourt(court1.getId(), T11)).as("ongoing blocks").isTrue();
        assertThat(reservationDao.existsUnfinishedByCourt(court1.getId(), T12)).as("end == now is finished").isFalse();
        assertThat(reservationDao.existsUnfinishedByCourt(court1.getId(), T13)).isFalse();
        assertThat(reservationDao.existsUnfinishedByCourt(court2.getId(), T10)).as("deleted reservation").isFalse();
    }

    @Test
    void should_detectUnfinishedReservations_when_existsUnfinishedByUser() {
        fixtures.persistReservation(court1, alice, T11, T12);
        fixtures.persistDeleted(Fixtures.reservation(court1, bob, T12, T13));
        fixtures.flushAndClear();

        assertThat(reservationDao.existsUnfinishedByUser(alice.getId(), T10)).isTrue();
        assertThat(reservationDao.existsUnfinishedByUser(alice.getId(), T11)).as("ongoing blocks").isTrue();
        assertThat(reservationDao.existsUnfinishedByUser(alice.getId(), T12)).as("end == now is finished").isFalse();
        assertThat(reservationDao.existsUnfinishedByUser(alice.getId(), T13)).isFalse();
        assertThat(reservationDao.existsUnfinishedByUser(bob.getId(), T10)).as("deleted reservation").isFalse();
    }

    // --- tie-breakers, findAll graph, argument checks ---

    @Test
    void should_orderByIdAsTieBreaker_when_createdAtIsEqual() {
        Reservation first = fixtures.persistReservation(court1, alice, T12, T13);
        Reservation second = fixtures.persistReservation(court1, bob, T10, T11);
        fixtures.flushAndClear();

        assertThat(reservationDao.findByCourtNumberOrderByCreatedAt(1)).extracting(Reservation::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void should_orderByIdAsTieBreaker_when_startTimeIsEqual() {
        Reservation first = fixtures.persistReservation(court2, alice, T10, T11);
        Reservation second = fixtures.persistReservation(court1, alice, T10, T11);
        fixtures.flushAndClear();

        assertThat(reservationDao.findAllOrderByStartTime()).extracting(Reservation::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(reservationDao.findByPhoneNumber(alice.getPhoneNumber(), false, null))
                .extracting(Reservation::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void should_fetchCourtAndUserGraph_when_findAll() {
        fixtures.persistReservation(court1, alice, T10, T11);
        fixtures.persistDeleted(Fixtures.reservation(court1, bob, T11, T12));
        fixtures.flushAndClear();

        List<Reservation> all = reservationDao.findAll();

        assertThat(all).hasSize(1);
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(all.get(0), "court")).isTrue();
        assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(all.get(0), "user")).isTrue();
        assertThat(all.get(0).getCourt().getSurfaceType().getName()).isNotBlank();
    }

    @Test
    void should_rejectNullArguments_when_queryParametersAreMandatory() {
        Long courtId = court1.getId();

        assertThatThrownBy(() -> reservationDao.existsOverlapping(null, T10, T11, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.existsOverlapping(courtId, null, T11, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.existsOverlapping(courtId, T10, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.findByPhoneNumber("+421", true, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.findByPhoneNumber(null, false, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.existsUnfinishedByCourt(null, T10))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.existsUnfinishedByCourt(courtId, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.existsUnfinishedByUser(null, T10))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservationDao.existsUnfinishedByUser(courtId, null))
                .isInstanceOf(NullPointerException.class);
    }
}
