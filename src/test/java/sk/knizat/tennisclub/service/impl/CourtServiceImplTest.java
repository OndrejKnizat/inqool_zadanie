package sk.knizat.tennisclub.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.knizat.tennisclub.dao.CourtDao;
import sk.knizat.tennisclub.dao.ReservationDao;
import sk.knizat.tennisclub.dao.SurfaceTypeDao;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.mapper.CourtMapper;
import sk.knizat.tennisclub.mapper.SurfaceTypeMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock
    private CourtDao courtDao;

    @Mock
    private SurfaceTypeDao surfaceTypeDao;

    @Mock
    private ReservationDao reservationDao;

    private CourtServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CourtServiceImpl(courtDao, surfaceTypeDao, reservationDao,
                new CourtMapper(new SurfaceTypeMapper()), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SurfaceType surface(Long id, String name) {
        SurfaceType surface = new SurfaceType();
        surface.setId(id);
        surface.setName(name);
        surface.setPricePerMinute(new BigDecimal("2.00"));
        return surface;
    }

    private static Court court(Long id, Integer number, SurfaceType surface) {
        Court court = new Court();
        court.setId(id);
        court.setCourtNumber(number);
        court.setName("Court " + number);
        court.setSurfaceType(surface);
        return court;
    }

    @Test
    void should_mapAllEntities_when_findAll() {
        SurfaceType clay = surface(1L, "Clay");
        when(courtDao.findAll()).thenReturn(List.of(court(1L, 1, clay), court(2L, 2, clay)));

        List<CourtResponse> result = service.findAll();

        assertThat(result).extracting(CourtResponse::id, CourtResponse::courtNumber, r -> r.surfaceType().name())
                .containsExactly(tuple(1L, 1, "Clay"), tuple(2L, 2, "Clay"));
    }

    @Test
    void should_returnResponseWithNestedSurface_when_findByIdExists() {
        when(courtDao.findById(1L)).thenReturn(Optional.of(court(1L, 7, surface(3L, "Grass"))));

        CourtResponse result = service.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.courtNumber()).isEqualTo(7);
        assertThat(result.name()).isEqualTo("Court 7");
        assertThat(result.surfaceType().id()).isEqualTo(3L);
        assertThat(result.surfaceType().name()).isEqualTo("Grass");
    }

    @Test
    void should_throwNotFound_when_findByIdMissing() {
        when(courtDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Court with id 9 not found");
    }

    @Test
    void should_saveEntityWithResolvedSurface_when_createWithFreeNumber() {
        SurfaceType clay = surface(1L, "Clay");
        when(courtDao.existsByCourtNumber(5)).thenReturn(false);
        when(surfaceTypeDao.findById(1L)).thenReturn(Optional.of(clay));
        when(courtDao.save(any(Court.class))).thenAnswer(inv -> {
            Court entity = inv.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        CourtResponse result = service.create(new CourtRequest(5, "  Centre court ", 1L));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.courtNumber()).isEqualTo(5);
        assertThat(result.name()).isEqualTo("Centre court");
        assertThat(result.surfaceType().id()).isEqualTo(1L);
    }

    @Test
    void should_storeNullName_when_createWithBlankName() {
        when(courtDao.existsByCourtNumber(5)).thenReturn(false);
        when(surfaceTypeDao.findById(1L)).thenReturn(Optional.of(surface(1L, "Clay")));
        when(courtDao.save(any(Court.class))).thenAnswer(inv -> inv.getArgument(0));

        CourtResponse result = service.create(new CourtRequest(5, "   ", 1L));

        assertThat(result.name()).isNull();
    }

    @Test
    void should_throwConflict_when_createWithDuplicateNumber() {
        when(courtDao.existsByCourtNumber(5)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CourtRequest(5, null, 1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Court with number 5 already exists");
        verify(surfaceTypeDao, never()).findById(any());
        verify(courtDao, never()).save(any());
    }

    @Test
    void should_throwValidation_when_createWithUnknownSurface() {
        when(courtDao.existsByCourtNumber(5)).thenReturn(false);
        when(surfaceTypeDao.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CourtRequest(5, null, 99L)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("surfaceTypeId 99 does not exist");
        verify(courtDao, never()).save(any());
    }

    @Test
    void should_updateFieldsAndSurface_when_updateWithFreeNumber() {
        Court existing = court(1L, 1, surface(1L, "Clay"));
        SurfaceType grass = surface(2L, "Grass");
        when(courtDao.findById(1L)).thenReturn(Optional.of(existing));
        when(courtDao.findByCourtNumber(8)).thenReturn(Optional.empty());
        when(surfaceTypeDao.findById(2L)).thenReturn(Optional.of(grass));
        when(courtDao.save(existing)).thenReturn(existing);

        CourtResponse result = service.update(1L, new CourtRequest(8, "Renamed", 2L));

        assertThat(result.courtNumber()).isEqualTo(8);
        assertThat(result.name()).isEqualTo("Renamed");
        assertThat(result.surfaceType().name()).isEqualTo("Grass");
        assertThat(existing.getSurfaceType()).isSameAs(grass);
    }

    @Test
    void should_allowUpdate_when_numberBelongsToSameCourt() {
        SurfaceType clay = surface(1L, "Clay");
        Court existing = court(1L, 1, clay);
        when(courtDao.findById(1L)).thenReturn(Optional.of(existing));
        when(courtDao.findByCourtNumber(1)).thenReturn(Optional.of(existing));
        when(surfaceTypeDao.findById(1L)).thenReturn(Optional.of(clay));
        when(courtDao.save(existing)).thenReturn(existing);

        CourtResponse result = service.update(1L, new CourtRequest(1, "New name", 1L));

        assertThat(result.name()).isEqualTo("New name");
    }

    @Test
    void should_throwConflict_when_updateNumberTakenByOther() {
        SurfaceType clay = surface(1L, "Clay");
        when(courtDao.findById(1L)).thenReturn(Optional.of(court(1L, 1, clay)));
        when(courtDao.findByCourtNumber(2)).thenReturn(Optional.of(court(2L, 2, clay)));

        assertThatThrownBy(() -> service.update(1L, new CourtRequest(2, null, 1L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2");
        verify(courtDao, never()).save(any());
    }

    @Test
    void should_throwValidation_when_updateWithUnknownSurface() {
        when(courtDao.findById(1L)).thenReturn(Optional.of(court(1L, 1, surface(1L, "Clay"))));
        when(courtDao.findByCourtNumber(1)).thenReturn(Optional.empty());
        when(surfaceTypeDao.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new CourtRequest(1, null, 99L)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("surfaceTypeId 99 does not exist");
        verify(courtDao, never()).save(any());
    }

    @Test
    void should_throwNotFound_when_updateMissing() {
        when(courtDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new CourtRequest(1, null, 1L)))
                .isInstanceOf(NotFoundException.class);
        verify(courtDao, never()).findByCourtNumber(any());
    }

    @Test
    void should_softDeleteWithClockInstant_when_deleteWithoutUnfinishedReservations() {
        Court existing = court(1L, 1, surface(1L, "Clay"));
        when(courtDao.findById(1L)).thenReturn(Optional.of(existing));
        when(reservationDao.existsUnfinishedByCourt(1L, NOW)).thenReturn(false);

        service.delete(1L);

        verify(courtDao).softDelete(existing, NOW);
    }

    @Test
    void should_throwConflict_when_deleteWithUnfinishedReservation() {
        when(courtDao.findById(1L)).thenReturn(Optional.of(court(1L, 1, surface(1L, "Clay"))));
        when(reservationDao.existsUnfinishedByCourt(1L, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("unfinished reservations");
        verify(courtDao, never()).softDelete(any(), any());
    }

    @Test
    void should_throwNotFound_when_deleteMissing() {
        when(courtDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(NotFoundException.class);
        verify(reservationDao, never()).existsUnfinishedByCourt(any(), any());
    }
}
