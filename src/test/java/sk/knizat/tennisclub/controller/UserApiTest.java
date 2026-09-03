package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.auth.TokenResponse;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.dto.user.CreateUserRequest;
import sk.knizat.tennisclub.dto.user.UpdateUserRequest;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.support.AbstractApiTest;
import sk.knizat.tennisclub.support.MutableClock;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of {@code /api/users} through the real stack (controller, security, service, DAO, H2).
 * The per-test ADMIN and USER accounts come from {@link AbstractApiTest}.
 */
class UserApiTest extends AbstractApiTest {

    private static final String BASE = "/api/users";
    private static final String ME = BASE + "/me";
    private static final String JANE_PHONE = "+421900000010";
    private static final String JANE_PASSWORD = "jane-secret";

    private ResponseEntity<UserResponse> create(String phone, String name, String password, RoleDto role) {
        return admin().postForEntity(BASE, new CreateUserRequest(phone, name, password, role), UserResponse.class);
    }

    private UserResponse created(String phone, String name, String password, RoleDto role) {
        ResponseEntity<UserResponse> response = create(phone, name, password, role);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody());
    }

    private ResponseEntity<UserResponse> update(Long id, UpdateUserRequest request) {
        return admin().exchange(BASE + "/" + id, HttpMethod.PUT, new HttpEntity<>(request), UserResponse.class);
    }

    private ResponseEntity<ProblemDetail> deleteAsProblem(Long id) {
        return admin().exchange(BASE + "/" + id, HttpMethod.DELETE, null, ProblemDetail.class);
    }

    private ResponseEntity<ProblemDetail> loginAsProblem(String phone, String password) {
        return rest.withBasicAuth(phone, password).postForEntity(LOGIN_PATH, null, ProblemDetail.class);
    }

    private UserResponse me(TestRestTemplate client) {
        ResponseEntity<UserResponse> response = client.getForEntity(ME, UserResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(response.getBody());
    }

    /** Creates a court through the API and books it for the phone number one day ahead of the clock. */
    private void createFutureReservationFor(String phone) {
        ResponseEntity<SurfaceTypeResponse> surface = admin().postForEntity("/api/surface-types",
                new SurfaceTypeRequest("Clay users", new BigDecimal("2.00")), SurfaceTypeResponse.class);
        assertThat(surface.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<CourtResponse> court = admin().postForEntity("/api/courts",
                new CourtRequest(1, null, Objects.requireNonNull(surface.getBody()).id()), CourtResponse.class);
        assertThat(court.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Instant start = MutableClock.DEFAULT_NOW.plus(Duration.ofDays(1));
        ResponseEntity<ReservationResponse> reservation = admin().postForEntity("/api/reservations",
                new CreateReservationRequest(1, start, start.plus(Duration.ofHours(1)), GameTypeDto.SINGLES,
                        phone, "Booker"), ReservationResponse.class);
        assertThat(reservation.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void should_createAccountThatCanLogIn_when_adminPosts() {
        ResponseEntity<UserResponse> response = create("+421 900-000 010", "  Jane  ", JANE_PASSWORD, RoleDto.USER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserResponse body = Objects.requireNonNull(response.getBody());
        assertThat(body.id()).isNotNull();
        assertThat(body.phoneNumber()).isEqualTo(JANE_PHONE);
        assertThat(body.name()).isEqualTo("Jane");
        assertThat(body.role()).isEqualTo(RoleDto.USER);
        assertThat(body.canLogin()).isTrue();
        assertThat(body.createdAt()).isEqualTo(MutableClock.DEFAULT_NOW);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo(BASE + "/" + body.id());

        TokenResponse tokens = login(JANE_PHONE, JANE_PASSWORD);
        UserResponse me = me(bearer(tokens.accessToken()));
        assertThat(me).isEqualTo(body);
    }

    @Test
    void should_neverExposePasswordHash_when_readingUsers() {
        UserResponse jane = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);

        String single = admin().getForObject(BASE + "/" + jane.id(), String.class);
        String list = admin().getForObject(BASE, String.class);
        String me = user().getForObject(ME, String.class);

        assertThat(single).contains("\"canLogin\":true").doesNotContainIgnoringCase("passwordHash");
        assertThat(list).doesNotContainIgnoringCase("passwordHash").doesNotContain("$2a$");
        assertThat(me).doesNotContainIgnoringCase("passwordHash");
    }

    @Test
    void should_listAndGetUsers_when_admin() {
        UserResponse jane = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);

        UserResponse[] list = admin().getForObject(BASE, UserResponse[].class);
        assertThat(Arrays.stream(list).map(UserResponse::phoneNumber))
                .containsExactly(ADMIN_PHONE, USER_PHONE, JANE_PHONE);

        ResponseEntity<UserResponse> fetched = admin().getForEntity(BASE + "/" + jane.id(), UserResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isEqualTo(jane);

        ResponseEntity<ProblemDetail> missing = admin().getForEntity(BASE + "/999999", ProblemDetail.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(missing.getBody()).getDetail()).isEqualTo("User with id 999999 not found");
    }

    @Test
    void should_returnOwnAccount_when_getMeAsUserAndAdmin() {
        UserResponse asUser = me(user());
        assertThat(asUser.phoneNumber()).isEqualTo(USER_PHONE);
        assertThat(asUser.name()).isEqualTo("User");
        assertThat(asUser.role()).isEqualTo(RoleDto.USER);
        assertThat(asUser.canLogin()).isTrue();

        UserResponse asAdmin = me(admin());
        assertThat(asAdmin.phoneNumber()).isEqualTo(ADMIN_PHONE);
        assertThat(asAdmin.role()).isEqualTo(RoleDto.ADMIN);
    }

    @Test
    void should_return403_when_userListsGetsCreatesUpdatesOrDeletes() {
        UserResponse jane = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);

        ResponseEntity<ProblemDetail> list = user().getForEntity(BASE, ProblemDetail.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(list.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(list.getBody()).getDetail()).isEqualTo("Insufficient role for this operation");

        assertThat(user().getForEntity(BASE + "/" + jane.id(), ProblemDetail.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user().postForEntity(BASE, new CreateUserRequest("+421900000011", "X", "x-password", RoleDto.USER),
                ProblemDetail.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user().exchange(BASE + "/" + jane.id(), HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest("X", RoleDto.ADMIN, null)), ProblemDetail.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user().exchange(BASE + "/" + jane.id(), HttpMethod.DELETE, null, ProblemDetail.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(rest.getForEntity(ME, ProblemDetail.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity(BASE, ProblemDetail.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return409_when_creatingDuplicatePhone() {
        ResponseEntity<ProblemDetail> duplicate = admin().postForEntity(BASE,
                new CreateUserRequest("+421 900 000 901", "Other", "other-secret", RoleDto.ADMIN), ProblemDetail.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(duplicate.getBody()).getDetail())
                .isEqualTo("User with phone number " + USER_PHONE + " already exists");
    }

    @Test
    void should_return400WithErrors_when_bodyInvalid() {
        ResponseEntity<ProblemDetail> response = admin().postForEntity(BASE,
                new CreateUserRequest("12", "", "short", null), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        Map<String, Object> properties = Objects.requireNonNull(response.getBody()).getProperties();
        assertThat(properties).containsKey("errors");
        assertThat(properties.get("errors").toString()).contains("phoneNumber", "name", "password", "role");
    }

    @Test
    void should_updateNameRoleAndPassword_when_adminPuts() {
        UserResponse jane = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);

        clock.advance(Duration.ofHours(1));
        ResponseEntity<UserResponse> renamed = update(jane.id(), new UpdateUserRequest("  Jane Doe ", RoleDto.ADMIN, null));
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse renamedBody = Objects.requireNonNull(renamed.getBody());
        assertThat(renamedBody.name()).isEqualTo("Jane Doe");
        assertThat(renamedBody.role()).isEqualTo(RoleDto.ADMIN);
        assertThat(renamedBody.phoneNumber()).isEqualTo(JANE_PHONE);
        assertThat(renamedBody.canLogin()).isTrue();
        assertThat(renamedBody.createdAt()).isEqualTo(MutableClock.DEFAULT_NOW);
        assertThat(renamedBody.updatedAt()).isEqualTo(MutableClock.DEFAULT_NOW.plus(Duration.ofHours(1)));
        // password untouched, new role is in the freshly issued token
        TokenResponse asAdmin = login(JANE_PHONE, JANE_PASSWORD);
        assertThat(bearer(asAdmin.accessToken()).getForEntity(BASE, UserResponse[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<UserResponse> repassworded =
                update(jane.id(), new UpdateUserRequest("Jane Doe", RoleDto.USER, "new-secret"));
        assertThat(repassworded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginAsProblem(JANE_PHONE, JANE_PASSWORD).getStatusCode()).as("old password")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(me(bearer(login(JANE_PHONE, "new-secret").accessToken())).name()).isEqualTo("Jane Doe");
    }

    @Test
    void should_return404_when_updatingMissingUser() {
        ResponseEntity<UserResponse> response = update(999_999L, new UpdateUserRequest("X", RoleDto.USER, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_return409_when_deletingOwnAccount() {
        UserResponse self = me(admin());

        ResponseEntity<ProblemDetail> blocked = deleteAsProblem(self.id());

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(blocked.getBody()).getDetail())
                .isEqualTo("User with id " + self.id() + " is the current user and cannot delete itself");
        assertThat(me(admin())).isEqualTo(self);
    }

    @Test
    void should_return409_when_deletingUserWithUnfinishedReservation() {
        UserResponse jane = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);
        createFutureReservationFor(JANE_PHONE);

        ResponseEntity<ProblemDetail> blocked = deleteAsProblem(jane.id());

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(Objects.requireNonNull(blocked.getBody()).getDetail())
                .isEqualTo("User with id " + jane.id() + " has unfinished reservations and cannot be deleted");
        assertThat(admin().getForEntity(BASE + "/" + jane.id(), UserResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_deleteUser_when_reservationIsAlreadyOver() {
        UserResponse jane = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);
        createFutureReservationFor(JANE_PHONE);
        clock.advance(Duration.ofDays(2));
        // the per-test admin token expired with the clock jump; log in again
        TestRestTemplate freshAdmin = bearer(login(ADMIN_PHONE, ADMIN_PASSWORD).accessToken());

        ResponseEntity<Void> deleted = freshAdmin.exchange(BASE + "/" + jane.id(), HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(freshAdmin.getForEntity(BASE + "/" + jane.id(), ProblemDetail.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // soft delete keeps the reservation history readable through the court listing (O-9); the phone
        // listing only covers non-deleted customers
        ReservationResponse[] history = freshAdmin.getForObject(
                URI.create("/api/reservations?courtNumber=1"), ReservationResponse[].class);
        assertThat(history).hasSize(1);
        assertThat(history[0].customer().phoneNumber()).isEqualTo(JANE_PHONE);
        assertThat(freshAdmin.getForObject(
                URI.create("/api/reservations?phoneNumber=" + JANE_PHONE.replace("+", "%2B")),
                ReservationResponse[].class)).isEmpty();
    }

    @Test
    void should_return409_when_demotingOrDeletingLastAdmin() {
        UserResponse self = me(admin());

        ResponseEntity<ProblemDetail> demoted = admin().exchange(BASE + "/" + self.id(), HttpMethod.PUT,
                new HttpEntity<>(new UpdateUserRequest("Admin", RoleDto.USER, null)), ProblemDetail.class);

        assertThat(demoted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(Objects.requireNonNull(demoted.getBody()).getDetail())
                .isEqualTo("User with id " + self.id() + " is the last administrator");
        assertThat(me(admin()).role()).isEqualTo(RoleDto.ADMIN);
    }

    @Test
    void should_softDeleteAndBlockLogin_when_adminDeletes() {
        UserResponse jane = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);
        TestRestTemplate janeClient = bearer(login(JANE_PHONE, JANE_PASSWORD).accessToken());

        ResponseEntity<Void> deleted = admin().exchange(BASE + "/" + jane.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProblemDetail> gone = admin().getForEntity(BASE + "/" + jane.id(), ProblemDetail.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        UserResponse[] list = admin().getForObject(BASE, UserResponse[].class);
        assertThat(Arrays.stream(list).map(UserResponse::id)).doesNotContain(jane.id());

        assertThat(loginAsProblem(JANE_PHONE, JANE_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // the already issued access token is still valid until it expires, but the account is gone
        ResponseEntity<ProblemDetail> meAfterDelete = janeClient.getForEntity(ME, ProblemDetail.class);
        assertThat(meAfterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(Objects.requireNonNull(meAfterDelete.getBody()).getDetail())
                .isEqualTo("User with phone number " + JANE_PHONE + " not found");

        assertThat(deleteAsProblem(jane.id()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_allowReuseOfPhone_when_originalIsSoftDeleted() {
        UserResponse first = created(JANE_PHONE, "Jane", JANE_PASSWORD, RoleDto.USER);
        ResponseEntity<Void> deleted = admin().exchange(BASE + "/" + first.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        UserResponse second = created(JANE_PHONE, "Jane again", "again-secret", RoleDto.ADMIN);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.role()).isEqualTo(RoleDto.ADMIN);
        assertThat(me(bearer(login(JANE_PHONE, "again-secret").accessToken())).id()).isEqualTo(second.id());
    }
}
