package sk.knizat.tennisclub.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests of {@link BaseEntity} timestamp hooks, soft delete and identity contract (no Spring). */
class BaseEntityTest {

    private static final Instant FIXED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-01-01T11:00:00Z");

    private static SurfaceType surfaceType(Long id) {
        SurfaceType e = new SurfaceType();
        e.setId(id);
        return e;
    }

    @Test
    void should_setDeletedFlagAndTimestamp_when_markDeleted() {
        SurfaceType e = new SurfaceType();
        assertThat(e.isDeleted()).isFalse();
        assertThat(e.getDeletedAt()).isNull();

        e.markDeleted(FIXED);

        assertThat(e.isDeleted()).isTrue();
        assertThat(e.getDeletedAt()).isEqualTo(FIXED);
    }

    @Test
    void should_keepOriginalDeletionTime_when_markDeletedTwice() {
        SurfaceType e = new SurfaceType();
        e.markDeleted(FIXED);

        e.markDeleted(LATER);

        assertThat(e.getDeletedAt()).isEqualTo(FIXED);
    }

    @Test
    void should_fillBothTimestamps_when_initTimestampsAndTimestampsNull() {
        SurfaceType e = new SurfaceType();

        e.initTimestamps(FIXED);

        assertThat(e.getCreatedAt()).isEqualTo(FIXED);
        assertThat(e.getUpdatedAt()).isEqualTo(FIXED);
    }

    @Test
    void should_keepPresetTimestamps_when_initTimestampsAndTimestampsAlreadySet() {
        SurfaceType e = new SurfaceType();
        e.initTimestamps(FIXED);

        e.initTimestamps(LATER);

        assertThat(e.getCreatedAt()).isEqualTo(FIXED);
        assertThat(e.getUpdatedAt()).isEqualTo(FIXED);
    }

    @Test
    void should_refreshOnlyUpdatedAt_when_touch() {
        SurfaceType e = new SurfaceType();
        e.initTimestamps(FIXED);

        e.touch(LATER);

        assertThat(e.getCreatedAt()).isEqualTo(FIXED);
        assertThat(e.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void should_beEqualWithSameHashCode_when_sameClassAndSameId() {
        SurfaceType a = surfaceType(1L);
        SurfaceType b = surfaceType(1L);

        assertThat(a).isEqualTo(b);
        assertThat(b).isEqualTo(a);
        assertThat(a).hasSameHashCodeAs(b);
    }

    @Test
    void should_notBeEqual_when_differentId() {
        assertThat(surfaceType(1L)).isNotEqualTo(surfaceType(2L));
    }

    @Test
    void should_beEqualOnlyToItself_when_idIsNull() {
        SurfaceType a = new SurfaceType();
        SurfaceType b = new SurfaceType();

        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void should_keepHashCodeStable_when_idAssignedAfterInsertionIntoSet() {
        SurfaceType e = new SurfaceType();
        Set<SurfaceType> set = new HashSet<>();
        set.add(e);

        e.setId(7L);

        assertThat(set).contains(e);
    }

    @Test
    void should_notBeEqual_when_differentClassOrNull() {
        SurfaceType surface = surfaceType(1L);
        Court court = new Court();
        court.setId(1L);

        assertThat(surface).isNotEqualTo(court);
        assertThat(surface).isNotEqualTo(null);
        assertThat(surface).isNotEqualTo("SurfaceType{id=1}");
    }

    @Test
    void should_containClassNameAndId_when_toString() {
        assertThat(surfaceType(42L)).hasToString("SurfaceType{id=42}");
        assertThat(new Court()).hasToString("Court{id=null}");
    }
}
