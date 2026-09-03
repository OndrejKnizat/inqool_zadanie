package sk.knizat.tennisclub.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sk.knizat.tennisclub.dao.SurfaceTypeDao;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.mapper.SurfaceTypeMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurfaceTypeServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock
    private SurfaceTypeDao dao;

    private SurfaceTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SurfaceTypeServiceImpl(dao, new SurfaceTypeMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SurfaceType surface(Long id, String name, String price) {
        SurfaceType surface = new SurfaceType();
        surface.setId(id);
        surface.setName(name);
        surface.setPricePerMinute(new BigDecimal(price));
        return surface;
    }

    @Test
    void should_mapAllEntities_when_findAll() {
        when(dao.findAll()).thenReturn(List.of(surface(1L, "Clay", "2.00"), surface(2L, "Grass", "3.00")));

        List<SurfaceTypeResponse> result = service.findAll();

        assertThat(result).extracting(SurfaceTypeResponse::id, SurfaceTypeResponse::name)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, "Clay"),
                        org.assertj.core.groups.Tuple.tuple(2L, "Grass"));
    }

    @Test
    void should_returnResponse_when_findByIdExists() {
        when(dao.findById(1L)).thenReturn(Optional.of(surface(1L, "Clay", "2.00")));

        SurfaceTypeResponse result = service.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Clay");
        assertThat(result.pricePerMinute()).isEqualTo(new BigDecimal("2.00"));
    }

    @Test
    void should_throwNotFound_when_findByIdMissing() {
        when(dao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("SurfaceType with id 9 not found");
    }

    @Test
    void should_saveTrimmedAndScaledEntity_when_createWithFreeName() {
        when(dao.findByName("Clay")).thenReturn(Optional.empty());
        when(dao.save(any(SurfaceType.class))).thenAnswer(inv -> {
            SurfaceType entity = inv.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        SurfaceTypeResponse result = service.create(new SurfaceTypeRequest(" Clay ", new BigDecimal("2.5")));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("Clay");
        assertThat(result.pricePerMinute()).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    void should_throwConflict_when_createWithDuplicateName() {
        when(dao.findByName("Clay")).thenReturn(Optional.of(surface(1L, "Clay", "2.00")));

        assertThatThrownBy(() -> service.create(new SurfaceTypeRequest("Clay", BigDecimal.ONE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Clay");
        verify(dao, never()).save(any());
    }

    @Test
    void should_updateFields_when_updateWithFreeName() {
        SurfaceType existing = surface(1L, "Clay", "2.00");
        when(dao.findById(1L)).thenReturn(Optional.of(existing));
        when(dao.findByName("Red clay")).thenReturn(Optional.empty());
        when(dao.save(existing)).thenReturn(existing);

        SurfaceTypeResponse result = service.update(1L, new SurfaceTypeRequest("Red clay", new BigDecimal("3")));

        assertThat(result.name()).isEqualTo("Red clay");
        assertThat(result.pricePerMinute()).isEqualTo(new BigDecimal("3.00"));
        assertThat(existing.getName()).isEqualTo("Red clay");
    }

    @Test
    void should_allowUpdate_when_nameBelongsToSameEntity() {
        SurfaceType existing = surface(1L, "Clay", "2.00");
        when(dao.findById(1L)).thenReturn(Optional.of(existing));
        when(dao.findByName("Clay")).thenReturn(Optional.of(existing));
        when(dao.save(existing)).thenReturn(existing);

        SurfaceTypeResponse result = service.update(1L, new SurfaceTypeRequest("Clay", new BigDecimal("9.99")));

        assertThat(result.pricePerMinute()).isEqualTo(new BigDecimal("9.99"));
    }

    @Test
    void should_throwConflict_when_updateNameTakenByOther() {
        when(dao.findById(1L)).thenReturn(Optional.of(surface(1L, "Clay", "2.00")));
        when(dao.findByName("Grass")).thenReturn(Optional.of(surface(2L, "Grass", "3.00")));

        assertThatThrownBy(() -> service.update(1L, new SurfaceTypeRequest("Grass", BigDecimal.ONE)))
                .isInstanceOf(ConflictException.class);
        verify(dao, never()).save(any());
    }

    @Test
    void should_throwNotFound_when_updateMissing() {
        when(dao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new SurfaceTypeRequest("X", BigDecimal.ONE)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void should_softDeleteWithClockInstant_when_deleteUnused() {
        SurfaceType existing = surface(1L, "Clay", "2.00");
        when(dao.findById(1L)).thenReturn(Optional.of(existing));
        when(dao.countCourtsUsing(1L)).thenReturn(0L);

        service.delete(1L);

        verify(dao).softDelete(existing, NOW);
    }

    @Test
    void should_throwConflict_when_deleteUsedByCourts() {
        when(dao.findById(1L)).thenReturn(Optional.of(surface(1L, "Clay", "2.00")));
        when(dao.countCourtsUsing(1L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 court(s)");
        verify(dao, never()).softDelete(any(), any());
    }

    @Test
    void should_throwNotFound_when_deleteMissing() {
        when(dao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(NotFoundException.class);
        verify(dao, never()).countCourtsUsing(any());
    }
}
