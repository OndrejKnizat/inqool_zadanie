package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.service.CourtService;
import sk.knizat.tennisclub.service.SurfaceTypeService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * End-to-end check of the data initialisation switch turned on: the context starts with
 * {@code app.data-init.enabled=true}, so {@link DataInitializer} must have seeded the database. The property
 * forks a dedicated context, and every context has its own in-memory database, so no cleanup is needed.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.data-init.enabled=true")
class DataInitializerEnabledTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private SurfaceTypeService surfaceTypeService;

    @Autowired
    private CourtService courtService;

    @Test
    void should_registerInitializerBean_when_dataInitEnabled() {
        assertThat(context.getBeanNamesForType(DataInitializer.class)).hasSize(1);
    }

    @Test
    void should_seedTwoSurfacesAndFourCourts_when_dataInitEnabled() {
        List<SurfaceTypeResponse> surfaces = surfaceTypeService.findAll();
        List<CourtResponse> courts = courtService.findAll();

        assertThat(surfaces).extracting(SurfaceTypeResponse::name, SurfaceTypeResponse::pricePerMinute)
                .containsExactly(
                        tuple("Antuka", new BigDecimal("2.50")),
                        tuple("Umelá tráva", new BigDecimal("3.00")));
        assertThat(courts).extracting(CourtResponse::courtNumber, CourtResponse::name, c -> c.surfaceType().name())
                .containsExactly(
                        tuple(1, "Court 1", "Antuka"),
                        tuple(2, "Court 2", "Antuka"),
                        tuple(3, "Court 3", "Umelá tráva"),
                        tuple(4, "Court 4", "Umelá tráva"));
    }
}
