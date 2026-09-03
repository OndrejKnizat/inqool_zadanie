package sk.knizat.tennisclub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.service.CourtService;
import sk.knizat.tennisclub.service.SurfaceTypeService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creates the initial data set (two surface types, four courts) at startup when {@code app.data-init.enabled=true}
 * (ARCHITECTURE.md section 6, O-17).
 * <p>
 * Everything goes through the service layer, so validation, soft delete and audit timestamps are honoured.
 * The runner is idempotent: when any non-deleted court already exists nothing is done; otherwise surface types
 * are created (an existing surface type with the same name is reused, which lets a partially completed
 * previous run finish) and then the courts. The runner itself is not transactional ({@code @Transactional}
 * is reserved for the service layer), so every service call commits on its own and the rules above are what
 * make a repeated start-up safe. Known limitation: if a run fails half-way through the courts, the courts
 * already committed make the next start-up skip the rest (rule from ARCHITECTURE.md section 6).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.data-init", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    /** Surface type seed together with the numbers of the courts built on it. */
    private record Seed(String surface, BigDecimal pricePerMinute, int... courtNumbers) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("Antuka", new BigDecimal("2.50"), 1, 2),
            new Seed("Umelá tráva", new BigDecimal("3.00"), 3, 4));

    private static final String COURT_NAME_PREFIX = "Court ";

    private final SurfaceTypeService surfaceTypeService;
    private final CourtService courtService;

    @Override
    public void run(ApplicationArguments args) {
        if (!courtService.findAll().isEmpty()) {
            log.info("Initial data skipped: courts already exist");
            return;
        }
        List<SurfaceTypeResponse> existingSurfaces = surfaceTypeService.findAll();
        List<String> createdSurfaces = new ArrayList<>();
        int createdCourts = 0;
        for (Seed seed : SEEDS) {
            Optional<SurfaceTypeResponse> existing = existingSurfaces.stream()
                    .filter(s -> s.name().equals(seed.surface()))
                    .findFirst();
            SurfaceTypeResponse surface;
            if (existing.isPresent()) {
                surface = existing.get();
                log.debug("Reusing existing surface type '{}' (id {})", surface.name(), surface.id());
            } else {
                surface = surfaceTypeService.create(new SurfaceTypeRequest(seed.surface(), seed.pricePerMinute()));
                createdSurfaces.add(surface.name());
            }
            for (int number : seed.courtNumbers()) {
                courtService.create(new CourtRequest(number, COURT_NAME_PREFIX + number, surface.id()));
                createdCourts++;
            }
        }
        log.info("Initial data created: {} surface type(s) {}, {} court(s)",
                createdSurfaces.size(), createdSurfaces, createdCourts);
    }

}
