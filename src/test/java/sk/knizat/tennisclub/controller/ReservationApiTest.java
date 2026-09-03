package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.reservation.UpdateReservationRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.support.AbstractApiTest;
import sk.knizat.tennisclub.support.MutableClock;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end test of {@code /api/reservations} through the real stack (controller, service, DAO, H2). */
class ReservationApiTest extends AbstractApiTest {

    private static final String BASE = "/api/reservations";
    private static final Instant NOW = MutableClock.DEFAULT_NOW;
    private static final Instant DAY1 = NOW.plus(Duration.ofDays(1));
    private static final String PHONE = "+421900000001";
    private static final String PHONE_SPACED = "+421 900 000 001";

    @BeforeEach
    void createCourts() {
        Long clay = createSurface("Clay", "2.00");
        Long grass = createSurface("Grass", "3.00");
        createCourt(1, "Centre court", clay);
        createCourt(2, "Court two", grass);
    }

    private Long createSurface(String name, String price) {
        ResponseEntity<SurfaceTypeResponse> response = rest.postForEntity("/api/surface-types",
                new SurfaceTypeRequest(name, new BigDecimal(price)), SurfaceTypeResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody()).id();
    }

    private void createCourt(Integer number, String name, Long surfaceId) {
        ResponseEntity<CourtResponse> response =
                rest.postForEntity("/api/courts", new CourtRequest(number, name, surfaceId), CourtResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private static CreateReservationRequest request(Integer court, Instant start, Duration length, GameTypeDto type,
                                                    String phone, String name) {
        return new CreateReservationRequest(court, start, start.plus(length), type, phone, name);
    }

    private ResponseEntity<ReservationResponse> create(CreateReservationRequest request) {
        return rest.postForEntity(BASE, request, ReservationResponse.class);
    }

    private ReservationResponse created(CreateReservationRequest request) {
        ResponseEntity<ReservationResponse> response = create(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody());
    }

    private ResponseEntity<ProblemDetail> createProblem(CreateReservationRequest request) {
        return rest.postForEntity(BASE, request, ProblemDetail.class);
    }

    private ResponseEntity<ReservationResponse> update(Long id, UpdateReservationRequest request) {
        return rest.exchange(BASE + "/" + id, HttpMethod.PUT, new HttpEntity<>(request), ReservationResponse.class);
    }

    /** Lists with a raw, already encoded query string (a {@code URI} is not re-encoded by the template). */
    private ReservationResponse[] list(String query) {
        return rest.getForObject(URI.create(BASE + query), ReservationResponse[].class);
    }

    /** Percent-encodes a query value; a literal {@code +} in a query string would arrive as a space. */
    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void should_createSinglesWithPriceAndLocation_when_postValid() {
        ResponseEntity<ReservationResponse> response =
                create(request(1, DAY1, Duration.ofMinutes(90), GameTypeDto.SINGLES, PHONE, "Jane"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ReservationResponse body = Objects.requireNonNull(response.getBody());
        assertThat(body.id()).isNotNull();
        assertThat(body.courtNumber()).isEqualTo(1);
        assertThat(body.courtName()).isEqualTo("Centre court");
        assertThat(body.startTime()).isEqualTo(DAY1);
        assertThat(body.endTime()).isEqualTo(DAY1.plus(Duration.ofMinutes(90)));
        assertThat(body.gameType()).isEqualTo(GameTypeDto.SINGLES);
        assertThat(body.price()).isEqualByComparingTo("180.00");
        assertThat(body.customer().phoneNumber()).isEqualTo(PHONE);
        assertThat(body.customer().name()).isEqualTo("Jane");
        assertThat(body.createdAt()).isEqualTo(NOW);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo(BASE + "/" + body.id());

        ResponseEntity<ReservationResponse> fetched =
                rest.getForEntity(response.getHeaders().getLocation(), ReservationResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isEqualTo(body);
    }

    @Test
    void should_applyDoublesMultiplier_when_postDoubles() {
        ReservationResponse body =
                created(request(2, DAY1, Duration.ofMinutes(60), GameTypeDto.DOUBLES, PHONE, "Jane"));

        // 60 min x 3.00 x 1.5
        assertThat(body.price()).isEqualByComparingTo("270.00");
        assertThat(body.gameType()).isEqualTo(GameTypeDto.DOUBLES);
    }

    @Test
    void should_listByCourtOrderedByCreation_when_courtNumberFilter() {
        // created in reverse start order; ordering must follow createdAt, not startTime
        ReservationResponse first = created(request(1, DAY1.plus(Duration.ofHours(4)), Duration.ofHours(1),
                GameTypeDto.SINGLES, PHONE, "Jane"));
        clock.advance(Duration.ofMinutes(1));
        ReservationResponse second = created(request(1, DAY1.plus(Duration.ofHours(2)), Duration.ofHours(1),
                GameTypeDto.SINGLES, PHONE, "Jane"));
        clock.advance(Duration.ofMinutes(1));
        ReservationResponse third =
                created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));
        created(request(2, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        ReservationResponse[] result = list("?courtNumber=1");

        assertThat(Arrays.stream(result).map(ReservationResponse::id))
                .containsExactly(first.id(), second.id(), third.id());
        assertThat(list("?courtNumber=99")).isEmpty();
    }

    @Test
    void should_listByPhoneWithFutureOnly_when_phoneNumberFilter() {
        ReservationResponse soon =
                created(request(1, NOW.plus(Duration.ofHours(1)), Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));
        ReservationResponse later =
                created(request(2, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));
        created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, "+421900000002", "Other"));

        assertThat(Arrays.stream(list("?phoneNumber=" + enc(PHONE))).map(ReservationResponse::id))
                .containsExactly(soon.id(), later.id());
        assertThat(Arrays.stream(list("?phoneNumber=" + enc(PHONE) + "&courtNumber=2")).map(ReservationResponse::id))
                .containsExactly(later.id());

        clock.advance(Duration.ofHours(3));
        assertThat(Arrays.stream(list("?phoneNumber=" + enc(PHONE) + "&futureOnly=true")).map(ReservationResponse::id))
                .containsExactly(later.id());
        assertThat(Arrays.stream(list("?phoneNumber=" + enc(PHONE) + "&futureOnly=false")).map(ReservationResponse::id))
                .containsExactly(soon.id(), later.id());
        assertThat(Arrays.stream(list("?futureOnly=true")).map(ReservationResponse::courtNumber))
                .containsExactly(2, 1);
    }

    @Test
    void should_listAllOrderedByStartTime_when_noFilter() {
        ReservationResponse late =
                created(request(1, DAY1.plus(Duration.ofHours(3)), Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));
        ReservationResponse early =
                created(request(2, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        assertThat(Arrays.stream(list("")).map(ReservationResponse::id)).containsExactly(early.id(), late.id());
    }

    @Test
    void should_return400WithDetail_when_intervalOverlaps() {
        created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        ResponseEntity<ProblemDetail> overlapping = createProblem(
                request(1, DAY1.plus(Duration.ofMinutes(30)), Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        assertThat(overlapping.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(overlapping.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(overlapping.getBody()).getTitle()).isEqualTo("Validation failed");
        assertThat(overlapping.getBody().getDetail())
                .isEqualTo("Reservation overlaps with an existing reservation on court 1");
        assertThat(list("?courtNumber=1")).hasSize(1);
    }

    @Test
    void should_allowTouchingIntervalsAndOtherCourt_when_notOverlapping() {
        created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        assertThat(create(request(1, DAY1.plus(Duration.ofHours(1)), Duration.ofHours(1), GameTypeDto.SINGLES,
                PHONE, "Jane")).getStatusCode()).as("touching after").isEqualTo(HttpStatus.CREATED);
        assertThat(create(request(1, DAY1.minus(Duration.ofHours(1)), Duration.ofHours(1), GameTypeDto.SINGLES,
                PHONE, "Jane")).getStatusCode()).as("touching before").isEqualTo(HttpStatus.CREATED);
        assertThat(create(request(2, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"))
                .getStatusCode()).as("same time on another court").isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void should_reuseCustomerWithStoredName_when_phoneMatchesAfterNormalisation() {
        created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE_SPACED, "Jane Original"));

        ReservationResponse second =
                created(request(2, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Different Name"));

        assertThat(second.customer().phoneNumber()).isEqualTo(PHONE);
        assertThat(second.customer().name()).isEqualTo("Jane Original");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT phone_number FROM app_user", String.class)).isEqualTo(PHONE);
        assertThat(list("?phoneNumber=" + enc(PHONE_SPACED))).hasSize(2);
    }

    @Test
    void should_moveAndRecalculatePrice_when_putValid() {
        ReservationResponse original =
                created(request(1, DAY1, Duration.ofMinutes(90), GameTypeDto.SINGLES, PHONE, "Jane"));
        assertThat(original.price()).isEqualByComparingTo("180.00");

        ResponseEntity<ReservationResponse> updated = update(original.id(), new UpdateReservationRequest(2,
                DAY1.plus(Duration.ofHours(2)), DAY1.plus(Duration.ofHours(3)), GameTypeDto.DOUBLES));

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        ReservationResponse body = Objects.requireNonNull(updated.getBody());
        assertThat(body.id()).isEqualTo(original.id());
        assertThat(body.courtNumber()).isEqualTo(2);
        assertThat(body.courtName()).isEqualTo("Court two");
        assertThat(body.startTime()).isEqualTo(DAY1.plus(Duration.ofHours(2)));
        // 60 min x 3.00 x 1.5
        assertThat(body.price()).isEqualByComparingTo("270.00");
        assertThat(body.customer()).isEqualTo(original.customer());
        assertThat(body.createdAt()).isEqualTo(original.createdAt());
        assertThat(list("?courtNumber=1")).isEmpty();
    }

    @Test
    void should_return400_when_putOverlapsAnotherReservation() {
        created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));
        ReservationResponse movable =
                created(request(2, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        ResponseEntity<ProblemDetail> conflict = rest.exchange(BASE + "/" + movable.id(), HttpMethod.PUT,
                new HttpEntity<>(new UpdateReservationRequest(1, DAY1, DAY1.plus(Duration.ofHours(1)),
                        GameTypeDto.SINGLES)), ProblemDetail.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(conflict.getBody()).getDetail())
                .isEqualTo("Reservation overlaps with an existing reservation on court 1");
    }

    @Test
    void should_keepOwnSlot_when_putOnlyChangesGameType() {
        ReservationResponse original =
                created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        ResponseEntity<ReservationResponse> updated = update(original.id(),
                new UpdateReservationRequest(1, DAY1, DAY1.plus(Duration.ofHours(1)), GameTypeDto.DOUBLES));

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(Objects.requireNonNull(updated.getBody()).price()).isEqualByComparingTo("180.00");
    }

    @Test
    void should_return404_when_putOrDeleteMissing() {
        ResponseEntity<ProblemDetail> put = rest.exchange(BASE + "/999999", HttpMethod.PUT,
                new HttpEntity<>(new UpdateReservationRequest(1, DAY1, DAY1.plus(Duration.ofHours(1)),
                        GameTypeDto.SINGLES)), ProblemDetail.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ProblemDetail> delete =
                rest.exchange(BASE + "/999999", HttpMethod.DELETE, null, ProblemDetail.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Objects.requireNonNull(delete.getBody()).getDetail())
                .isEqualTo("Reservation with id 999999 not found");
    }

    @Test
    void should_return204ThenGone_when_deleted() {
        ReservationResponse reservation =
                created(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        ResponseEntity<Void> deleted =
                rest.exchange(BASE + "/" + reservation.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProblemDetail> gone = rest.getForEntity(BASE + "/" + reservation.id(), ProblemDetail.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(list("")).isEmpty();
        // soft delete: the row is still there and its slot is free again
        assertThat(jdbcTemplate.queryForObject("SELECT deleted FROM reservation WHERE id = ?", Boolean.class,
                reservation.id())).isTrue();
        assertThat(create(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void should_return404_when_courtNumberUnknown() {
        ResponseEntity<ProblemDetail> response =
                createProblem(request(42, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail()).isEqualTo("Court with number 42 not found");
    }

    @Test
    void should_return400_when_intervalInvalid() {
        assertProblem(request(1, DAY1, Duration.ofMinutes(-30), GameTypeDto.SINGLES, PHONE, "Jane"),
                "startTime must be before endTime");
        assertProblem(request(1, NOW.minus(Duration.ofMinutes(1)), Duration.ofHours(1), GameTypeDto.SINGLES, PHONE,
                "Jane"), "startTime must not be in the past");
        assertProblem(request(1, DAY1, Duration.ofMinutes(10), GameTypeDto.SINGLES, PHONE, "Jane"),
                "Reservation must last at least 15 minutes");
        assertProblem(request(1, DAY1, Duration.ofHours(5), GameTypeDto.SINGLES, PHONE, "Jane"),
                "Reservation must last at most 240 minutes");
        assertProblem(request(1, DAY1.plusSeconds(30), Duration.ofHours(1), GameTypeDto.SINGLES, PHONE, "Jane"),
                "startTime and endTime must be whole minutes (seconds must be zero)");
        ResponseEntity<ProblemDetail> badPhone =
                createProblem(request(1, DAY1, Duration.ofHours(1), GameTypeDto.SINGLES, "+421 9-0", "Jane"));
        assertThat(badPhone.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(badPhone.getBody()).getProperties()).containsKey("errors");
        assertThat(list("")).isEmpty();
    }

    private void assertProblem(CreateReservationRequest request, String expectedDetail) {
        ResponseEntity<ProblemDetail> response = createProblem(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail()).isEqualTo(expectedDetail);
    }

    @Test
    void should_return400WithErrors_when_bodyFailsBeanValidation() {
        ResponseEntity<ProblemDetail> response =
                createProblem(new CreateReservationRequest(0, null, null, null, "abc", " "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(response.getBody()).getProperties()).containsKey("errors");
    }
}
