package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.service.CourtService;
import sk.knizat.tennisclub.service.SurfaceTypeService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private SurfaceTypeService surfaceTypeService;

    @Mock
    private CourtService courtService;

    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new DataInitializer(surfaceTypeService, courtService);
    }

    @Test
    void should_createTwoSurfacesAndFourCourts_when_databaseEmpty() {
        when(courtService.findAll()).thenReturn(List.of());
        when(surfaceTypeService.findAll()).thenReturn(List.of());
        when(surfaceTypeService.create(any())).thenReturn(surface(10L, "Antuka", "2.50"), surface(20L, "Umelá tráva", "3.00"));
        when(courtService.create(any())).thenReturn(court(1L, 1));

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<SurfaceTypeRequest> surfaceCaptor = ArgumentCaptor.forClass(SurfaceTypeRequest.class);
        verify(surfaceTypeService, times(2)).create(surfaceCaptor.capture());
        assertThat(surfaceCaptor.getAllValues()).containsExactly(
                new SurfaceTypeRequest("Antuka", new BigDecimal("2.50")),
                new SurfaceTypeRequest("Umelá tráva", new BigDecimal("3.00")));

        ArgumentCaptor<CourtRequest> courtCaptor = ArgumentCaptor.forClass(CourtRequest.class);
        verify(courtService, times(4)).create(courtCaptor.capture());
        assertThat(courtCaptor.getAllValues()).containsExactly(
                new CourtRequest(1, "Court 1", 10L),
                new CourtRequest(2, "Court 2", 10L),
                new CourtRequest(3, "Court 3", 20L),
                new CourtRequest(4, "Court 4", 20L));
    }

    @Test
    void should_doNothing_when_courtsAlreadyExist() {
        when(courtService.findAll()).thenReturn(List.of(court(1L, 1)));

        initializer.run(new DefaultApplicationArguments());

        verify(courtService).findAll();
        verify(courtService, never()).create(any());
        verifyNoMoreInteractions(courtService, surfaceTypeService);
    }

    @Test
    void should_reuseExistingSurfaceAndCreateMissingOne_when_previousRunWasPartial() {
        when(courtService.findAll()).thenReturn(List.of());
        when(surfaceTypeService.findAll()).thenReturn(List.of(surface(5L, "Antuka", "2.50")));
        when(surfaceTypeService.create(any())).thenReturn(surface(20L, "Umelá tráva", "3.00"));
        when(courtService.create(any())).thenReturn(court(1L, 1));

        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<SurfaceTypeRequest> surfaceCaptor = ArgumentCaptor.forClass(SurfaceTypeRequest.class);
        verify(surfaceTypeService).create(surfaceCaptor.capture());
        assertThat(surfaceCaptor.getValue()).isEqualTo(new SurfaceTypeRequest("Umelá tráva", new BigDecimal("3.00")));

        ArgumentCaptor<CourtRequest> courtCaptor = ArgumentCaptor.forClass(CourtRequest.class);
        verify(courtService, times(4)).create(courtCaptor.capture());
        assertThat(courtCaptor.getAllValues()).extracting(CourtRequest::surfaceTypeId)
                .containsExactly(5L, 5L, 20L, 20L);
    }

    private static SurfaceTypeResponse surface(Long id, String name, String price) {
        return new SurfaceTypeResponse(id, name, new BigDecimal(price), NOW, NOW);
    }

    private static CourtResponse court(Long id, int number) {
        return new CourtResponse(id, number, "Court " + number, surface(1L, "Antuka", "2.50"), NOW, NOW);
    }
}
