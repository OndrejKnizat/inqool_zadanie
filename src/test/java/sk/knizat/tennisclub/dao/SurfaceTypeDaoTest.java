package sk.knizat.tennisclub.dao;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.support.Fixtures;
import sk.knizat.tennisclub.support.MutableClock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration tests of {@link SurfaceTypeDao} and, through it, of the generic {@link AbstractDao} methods. */
class SurfaceTypeDaoTest extends AbstractDaoTest {

    @Autowired
    private SurfaceTypeDao surfaceTypeDao;

    // --- generic operations (AbstractDao) ---

    @Test
    void should_persistAndStampAudit_when_saveNewEntity() {
        SurfaceType saved = surfaceTypeDao.save(Fixtures.surfaceType("Clay", "2.50"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(saved.getUpdatedAt()).isEqualTo(clock.instant());
        fixtures.flushAndClear();
        assertThat(surfaceTypeDao.findById(saved.getId())).get()
                .extracting(SurfaceType::getName).isEqualTo("Clay");
    }

    @Test
    void should_mergeAndReturnManagedInstance_when_saveDetachedEntity() {
        SurfaceType saved = fixtures.persistSurfaceType("Grass");
        fixtures.flushAndClear();

        SurfaceType detached = new SurfaceType();
        detached.setId(saved.getId());
        detached.setName("Grass renamed");
        detached.setPricePerMinute(new BigDecimal("3.00"));
        SurfaceType merged = surfaceTypeDao.save(detached);
        fixtures.flushAndClear();

        assertThat(merged).isNotSameAs(detached);
        assertThat(merged.getId()).isEqualTo(saved.getId());
        assertThat(surfaceTypeDao.findById(saved.getId())).get()
                .extracting(SurfaceType::getName).isEqualTo("Grass renamed");
        assertThat(surfaceTypeDao.findAll()).hasSize(1);
    }

    @Test
    void should_stampUpdatedAtBeforeReturning_when_saveModifiedManagedEntity() {
        SurfaceType saved = fixtures.persistSurfaceType("Carpet");
        Instant later = clock.advance(Duration.ofHours(2));

        saved.setName("Carpet renamed");
        SurfaceType result = surfaceTypeDao.save(saved);

        assertThat(result.getUpdatedAt()).isEqualTo(later);
        assertThat(result.getCreatedAt()).isEqualTo(MutableClock.DEFAULT_NOW);
    }

    @Test
    void should_stampUpdatedAtBeforeReturning_when_softDelete() {
        SurfaceType saved = fixtures.persistSurfaceType("Carpet");
        Instant later = clock.advance(Duration.ofHours(2));

        surfaceTypeDao.softDelete(saved, later);

        assertThat(saved.getDeletedAt()).isEqualTo(later);
        assertThat(saved.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void should_returnEntity_when_findByIdOfExisting() {
        SurfaceType saved = fixtures.persistSurfaceType("Hard");
        fixtures.flushAndClear();

        Optional<SurfaceType> found = surfaceTypeDao.findById(saved.getId());

        assertThat(found).get().extracting(SurfaceType::getName).isEqualTo("Hard");
    }

    @Test
    void should_returnEmpty_when_findByIdOfMissingOrNull() {
        assertThat(surfaceTypeDao.findById(999_999L)).isEmpty();
        assertThat(surfaceTypeDao.findById(null)).isEmpty();
    }

    @Test
    void should_returnEmpty_when_findByIdOfSoftDeleted() {
        SurfaceType deleted = fixtures.persistDeleted(Fixtures.surfaceType("Gone", "1.00"));
        fixtures.flushAndClear();

        assertThat(surfaceTypeDao.findById(deleted.getId())).isEmpty();
        assertThat(em.find(SurfaceType.class, deleted.getId())).as("row still physically present").isNotNull();
    }

    @Test
    void should_listOnlyNonDeletedOrderedById_when_findAll() {
        SurfaceType second = fixtures.persistSurfaceType("B");
        SurfaceType first = fixtures.persistSurfaceType("A");
        fixtures.persistDeleted(Fixtures.surfaceType("Deleted", "1.00"));
        fixtures.flushAndClear();

        List<SurfaceType> all = surfaceTypeDao.findAll();

        assertThat(all).extracting(SurfaceType::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    void should_markDeletedAndHideFromReads_when_softDelete() {
        SurfaceType saved = fixtures.persistSurfaceType("Carpet");
        Instant deletionTime = Instant.parse("2026-07-01T00:00:00Z");

        surfaceTypeDao.softDelete(saved, deletionTime);
        fixtures.flushAndClear();

        SurfaceType raw = em.find(SurfaceType.class, saved.getId());
        assertThat(raw.isDeleted()).isTrue();
        assertThat(raw.getDeletedAt()).isEqualTo(deletionTime);
        assertThat(surfaceTypeDao.findById(saved.getId())).isEmpty();
        assertThat(surfaceTypeDao.existsById(saved.getId())).isFalse();
        assertThat(surfaceTypeDao.findAll()).isEmpty();
        assertThat(surfaceTypeDao.findByName("Carpet")).isEmpty();
    }

    @Test
    void should_softDeleteDetachedEntity_when_entityNotManaged() {
        SurfaceType saved = fixtures.persistSurfaceType("Detached");
        fixtures.flushAndClear();
        SurfaceType detached = em.find(SurfaceType.class, saved.getId());
        em.detach(detached);

        surfaceTypeDao.softDelete(detached, Instant.parse("2026-07-01T00:00:00Z"));
        fixtures.flushAndClear();

        assertThat(surfaceTypeDao.findById(saved.getId())).isEmpty();
    }

    @Test
    void should_reportExistence_when_existsById() {
        SurfaceType saved = fixtures.persistSurfaceType("Exists");
        SurfaceType deleted = fixtures.persistDeleted(Fixtures.surfaceType("Deleted", "1.00"));
        fixtures.flushAndClear();

        assertThat(surfaceTypeDao.existsById(saved.getId())).isTrue();
        assertThat(surfaceTypeDao.existsById(deleted.getId())).isFalse();
        assertThat(surfaceTypeDao.existsById(999_999L)).isFalse();
        assertThat(surfaceTypeDao.existsById(null)).isFalse();
    }

    // --- specific queries ---

    @Test
    void should_findByName_when_nonDeletedExists() {
        SurfaceType saved = fixtures.persistSurfaceType("Clay");
        fixtures.flushAndClear();

        assertThat(surfaceTypeDao.findByName("Clay")).get().extracting(SurfaceType::getId).isEqualTo(saved.getId());
        assertThat(surfaceTypeDao.findByName("clay")).as("exact match only").isEmpty();
        assertThat(surfaceTypeDao.findByName("Unknown")).isEmpty();
    }

    @Test
    void should_ignoreDeleted_when_findByName() {
        fixtures.persistDeleted(Fixtures.surfaceType("Clay", "1.00"));
        fixtures.flushAndClear();

        assertThat(surfaceTypeDao.findByName("Clay")).isEmpty();
    }

    @Test
    void should_countOnlyNonDeletedCourts_when_countCourtsUsing() {
        SurfaceType clay = fixtures.persistSurfaceType("Clay");
        SurfaceType grass = fixtures.persistSurfaceType("Grass");
        fixtures.persistCourt(1, clay);
        fixtures.persistCourt(2, clay);
        fixtures.persistDeleted(Fixtures.court(3, clay));
        fixtures.persistCourt(4, grass);
        fixtures.flushAndClear();

        assertThat(surfaceTypeDao.countCourtsUsing(clay.getId())).isEqualTo(2);
        assertThat(surfaceTypeDao.countCourtsUsing(grass.getId())).isEqualTo(1);
        assertThat(surfaceTypeDao.countCourtsUsing(999_999L)).isZero();
    }

    @Test
    void should_countCourtsOfDeletedSurface_when_courtsStillActive() {
        SurfaceType clay = fixtures.persistDeleted(Fixtures.surfaceType("Clay", "1.00"));
        Court court = fixtures.persistCourt(1, clay);
        fixtures.flushAndClear();

        assertThat(surfaceTypeDao.countCourtsUsing(clay.getId())).isEqualTo(1);
    }

    @Test
    void should_throwNonUniqueResult_when_twoActiveRowsShareBusinessKey() {
        fixtures.persistSurfaceType("Duplicate");
        fixtures.persistSurfaceType("Duplicate");
        fixtures.flushAndClear();

        assertThatThrownBy(() -> surfaceTypeDao.findByName("Duplicate"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }
}
