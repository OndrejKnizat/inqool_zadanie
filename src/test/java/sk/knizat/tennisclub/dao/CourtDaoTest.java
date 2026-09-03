package sk.knizat.tennisclub.dao;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.support.Fixtures;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration tests of {@link CourtDao}. */
class CourtDaoTest extends AbstractDaoTest {

    @Autowired
    private CourtDao courtDao;

    @Test
    void should_findByCourtNumber_when_nonDeletedExists() {
        Court court = fixtures.persistCourt(7);
        fixtures.flushAndClear();

        Optional<Court> found = courtDao.findByCourtNumber(7);

        assertThat(found).get().extracting(Court::getId).isEqualTo(court.getId());
        assertThat(found.get().getSurfaceType().getName()).isEqualTo("Surface of court 7");
        assertThat(courtDao.findByCourtNumber(8)).isEmpty();
    }

    @Test
    void should_ignoreDeleted_when_findByCourtNumber() {
        SurfaceType surface = fixtures.persistSurfaceType("Clay");
        fixtures.persistDeleted(Fixtures.court(7, surface));
        fixtures.flushAndClear();

        assertThat(courtDao.findByCourtNumber(7)).isEmpty();
    }

    @Test
    void should_returnActiveOne_when_deletedCourtSharesNumber() {
        SurfaceType surface = fixtures.persistSurfaceType("Clay");
        fixtures.persistDeleted(Fixtures.court(7, surface));
        Court active = fixtures.persistCourt(7, surface);
        fixtures.flushAndClear();

        assertThat(courtDao.findByCourtNumber(7)).get().extracting(Court::getId).isEqualTo(active.getId());
    }

    @Test
    void should_reportExistence_when_existsByCourtNumber() {
        SurfaceType surface = fixtures.persistSurfaceType("Clay");
        fixtures.persistCourt(1, surface);
        fixtures.persistDeleted(Fixtures.court(2, surface));
        fixtures.flushAndClear();

        assertThat(courtDao.existsByCourtNumber(1)).isTrue();
        assertThat(courtDao.existsByCourtNumber(2)).as("soft-deleted court").isFalse();
        assertThat(courtDao.existsByCourtNumber(3)).isFalse();
    }

    @Test
    void should_hideDeletedFromGenericReads_when_courtSoftDeleted() {
        Court court = fixtures.persistCourt(1);
        courtDao.softDelete(court, clock.instant());
        fixtures.flushAndClear();

        assertThat(courtDao.findById(court.getId())).isEmpty();
        assertThat(courtDao.existsById(court.getId())).isFalse();
        assertThat(courtDao.findAll()).isEmpty();
        assertThat(em.find(Court.class, court.getId()).getDeletedAt()).isEqualTo(clock.instant());
    }

    @Test
    void should_lockAndReturnCourt_when_findByIdForUpdateOfActive() {
        Court court = fixtures.persistCourt(1);
        fixtures.flushAndClear();

        Optional<Court> locked = courtDao.findByIdForUpdate(court.getId());

        assertThat(locked).get().extracting(Court::getCourtNumber).isEqualTo(1);
        assertThat(em.getLockMode(locked.get())).isEqualTo(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void should_returnEmpty_when_findByIdForUpdateOfDeletedMissingOrNull() {
        SurfaceType surface = fixtures.persistSurfaceType("Clay");
        Court deleted = fixtures.persistDeleted(Fixtures.court(1, surface));
        fixtures.flushAndClear();

        assertThat(courtDao.findByIdForUpdate(deleted.getId())).isEmpty();
        assertThat(courtDao.findByIdForUpdate(999_999L)).isEmpty();
        assertThat(courtDao.findByIdForUpdate(null)).isEmpty();
    }

    @Test
    void should_persistNewCourt_when_save() {
        SurfaceType surface = fixtures.persistSurfaceType("Clay");

        Court saved = courtDao.save(Fixtures.court(3, surface));
        fixtures.flushAndClear();

        assertThat(courtDao.findAll()).extracting(Court::getId).containsExactly(saved.getId());
    }

    @Test
    void should_fetchSurfaceTypeEagerly_when_findAll() {
        fixtures.persistCourt(1);
        fixtures.persistCourt(2);
        fixtures.persistDeleted(Fixtures.court(3, fixtures.persistSurfaceType("Deleted court surface")));
        fixtures.flushAndClear();

        List<Court> courts = courtDao.findAll();

        assertThat(courts).extracting(Court::getCourtNumber).containsExactly(1, 2);
        assertThat(courts).allSatisfy(court -> assertThat(Hibernate.isInitialized(court.getSurfaceType())).isTrue());
    }
}
