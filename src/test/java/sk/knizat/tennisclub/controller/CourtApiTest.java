package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.support.AbstractApiTest;
import sk.knizat.tennisclub.support.MutableClock;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of {@code /api/courts} through the real stack (controller, service, DAO, H2). Surface types
 * are created through their own API; the reservation API does not exist yet, so the blocking reservation is
 * inserted with {@link JdbcTemplate}.
 */
class CourtApiTest extends AbstractApiTest {

    private static final String BASE = "/api/courts";
    private static final String SURFACES = "/api/surface-types";

    private Long createSurface(String name) {
        ResponseEntity<SurfaceTypeResponse> response =
                admin().postForEntity(SURFACES, new SurfaceTypeRequest(name, new BigDecimal("2.00")), SurfaceTypeResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody()).id();
    }

    private ResponseEntity<CourtResponse> create(Integer number, String name, Long surfaceId) {
        return admin().postForEntity(BASE, new CourtRequest(number, name, surfaceId), CourtResponse.class);
    }

    /** Inserts a user and a reservation on the court directly, ending at {@code end}. */
    private void insertReservation(Long courtId, Instant start, Instant end) {
        Timestamp now = Timestamp.from(MutableClock.DEFAULT_NOW);
        jdbcTemplate.update("INSERT INTO app_user (phone_number, name, role, created_at, updated_at, deleted) "
                + "VALUES (?, ?, ?, ?, ?, FALSE)", "+421900000099", "Blocker", "USER", now, now);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE phone_number = ?", Long.class,
                "+421900000099");
        jdbcTemplate.update("INSERT INTO reservation (court_id, user_id, start_time, end_time, game_type, price, "
                        + "created_at, updated_at, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)",
                courtId, userId, Timestamp.from(start), Timestamp.from(end), "SINGLES", new BigDecimal("100.00"),
                now, now);
    }

    @Test
    void should_completeLifecycle_when_createGetListUpdateDelete() {
        Long clay = createSurface("Clay lifecycle");
        Long grass = createSurface("Grass lifecycle");

        ResponseEntity<CourtResponse> created = create(1, "  Centre court  ", clay);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CourtResponse body = Objects.requireNonNull(created.getBody());
        assertThat(body.id()).isNotNull();
        assertThat(body.courtNumber()).isEqualTo(1);
        assertThat(body.name()).isEqualTo("Centre court");
        assertThat(body.surfaceType().id()).isEqualTo(clay);
        assertThat(body.surfaceType().name()).isEqualTo("Clay lifecycle");
        assertThat(body.createdAt()).isEqualTo(MutableClock.DEFAULT_NOW);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo(BASE + "/" + body.id());

        ResponseEntity<CourtResponse> fetched =
                admin().getForEntity(created.getHeaders().getLocation(), CourtResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isEqualTo(body);

        CourtResponse[] list = admin().getForObject(BASE, CourtResponse[].class);
        assertThat(Arrays.stream(list).map(CourtResponse::id)).containsExactly(body.id());
        assertThat(list[0].surfaceType().name()).isEqualTo("Clay lifecycle");

        ResponseEntity<CourtResponse> sameNumber = admin().exchange(BASE + "/" + body.id(), HttpMethod.PUT,
                new HttpEntity<>(new CourtRequest(1, "Centre court", clay)), CourtResponse.class);
        assertThat(sameNumber.getStatusCode()).as("update keeping own number").isEqualTo(HttpStatus.OK);

        clock.advance(Duration.ofHours(1));
        ResponseEntity<CourtResponse> updated = admin().exchange(BASE + "/" + body.id(), HttpMethod.PUT,
                new HttpEntity<>(new CourtRequest(2, "   ", grass)), CourtResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        CourtResponse updatedBody = Objects.requireNonNull(updated.getBody());
        assertThat(updatedBody.courtNumber()).isEqualTo(2);
        assertThat(updatedBody.name()).isNull();
        assertThat(updatedBody.surfaceType().id()).isEqualTo(grass);
        assertThat(updatedBody.createdAt()).isEqualTo(MutableClock.DEFAULT_NOW);
        assertThat(updatedBody.updatedAt()).isEqualTo(MutableClock.DEFAULT_NOW.plus(Duration.ofHours(1)));

        ResponseEntity<Void> deleted = admin().exchange(BASE + "/" + body.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProblemDetail> gone = admin().getForEntity(BASE + "/" + body.id(), ProblemDetail.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gone.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(gone.getBody()).getDetail())
                .isEqualTo("Court with id " + body.id() + " not found");

        CourtResponse[] afterDelete = admin().getForObject(BASE, CourtResponse[].class);
        assertThat(Arrays.stream(afterDelete).map(CourtResponse::id)).doesNotContain(body.id());
    }

    @Test
    void should_return409_when_creatingDuplicateNumber() {
        Long clay = createSurface("Clay duplicate");
        assertThat(create(5, null, clay).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ProblemDetail> duplicate =
                admin().postForEntity(BASE, new CourtRequest(5, "Other", clay), ProblemDetail.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(duplicate.getBody()).getTitle()).isEqualTo("Conflict");
        assertThat(duplicate.getBody().getDetail()).isEqualTo("Court with number 5 already exists");
    }

    @Test
    void should_return409_when_updatingToNumberOfAnotherCourt() {
        Long clay = createSurface("Clay update conflict");
        assertThat(create(1, null, clay).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long secondId = Objects.requireNonNull(create(2, null, clay).getBody()).id();

        ResponseEntity<ProblemDetail> conflict = admin().exchange(BASE + "/" + secondId, HttpMethod.PUT,
                new HttpEntity<>(new CourtRequest(1, null, clay)), ProblemDetail.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(conflict.getBody()).getDetail()).isEqualTo("Court with number 1 already exists");
    }

    @Test
    void should_allowReuseOfNumber_when_originalIsSoftDeleted() {
        Long clay = createSurface("Clay reuse");
        Long firstId = Objects.requireNonNull(create(3, null, clay).getBody()).id();
        admin().delete(BASE + "/" + firstId);

        ResponseEntity<CourtResponse> second = create(3, null, clay);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(Objects.requireNonNull(second.getBody()).id()).isNotEqualTo(firstId);
    }

    @Test
    void should_return400_when_surfaceTypeDoesNotExist() {
        ResponseEntity<ProblemDetail> response =
                admin().postForEntity(BASE, new CourtRequest(1, null, 999_999L), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail())
                .isEqualTo("surfaceTypeId 999999 does not exist");
    }

    @Test
    void should_return400_when_surfaceTypeIsSoftDeleted() {
        Long clay = createSurface("Clay deleted");
        admin().delete(SURFACES + "/" + clay);

        ResponseEntity<ProblemDetail> response =
                admin().postForEntity(BASE, new CourtRequest(1, null, clay), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail())
                .isEqualTo("surfaceTypeId " + clay + " does not exist");
    }

    @Test
    void should_return400WithErrors_when_bodyInvalid() {
        ResponseEntity<ProblemDetail> response =
                admin().postForEntity(BASE, new CourtRequest(0, null, null), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(response.getBody()).getProperties()).containsKey("errors");
    }

    @Test
    void should_return409_when_deletingCourtWithUnfinishedReservation() {
        Long clay = createSurface("Clay blocked delete");
        Long courtId = Objects.requireNonNull(create(4, null, clay).getBody()).id();
        insertReservation(courtId, MutableClock.DEFAULT_NOW.plus(Duration.ofDays(1)),
                MutableClock.DEFAULT_NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)));

        ResponseEntity<ProblemDetail> blocked =
                admin().exchange(BASE + "/" + courtId, HttpMethod.DELETE, null, ProblemDetail.class);

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(blocked.getBody()).getDetail())
                .isEqualTo("Court with id " + courtId + " has unfinished reservations and cannot be deleted");
        assertThat(admin().getForEntity(BASE + "/" + courtId, CourtResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_deleteCourt_when_onlyPastReservationsExist() {
        Long clay = createSurface("Clay past reservation");
        Long courtId = Objects.requireNonNull(create(6, null, clay).getBody()).id();
        insertReservation(courtId, MutableClock.DEFAULT_NOW.minus(Duration.ofHours(2)),
                MutableClock.DEFAULT_NOW.minus(Duration.ofHours(1)));

        ResponseEntity<Void> deleted = admin().exchange(BASE + "/" + courtId, HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
