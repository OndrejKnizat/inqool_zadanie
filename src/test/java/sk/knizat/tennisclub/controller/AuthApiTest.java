package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import sk.knizat.tennisclub.config.AppProperties;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.auth.RefreshTokenRequest;
import sk.knizat.tennisclub.dto.auth.TokenResponse;
import sk.knizat.tennisclub.dto.court.CourtRequest;
import sk.knizat.tennisclub.dto.court.CourtResponse;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.support.AbstractApiTest;
import sk.knizat.tennisclub.support.MutableClock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end security scenarios: login, bearer access, role matrix, expiry, refresh, public OpenAPI. */
class AuthApiTest extends AbstractApiTest {

    private static final String COURTS = "/api/courts";
    private static final String REFRESH = "/api/auth/refresh";

    @Autowired
    private AppProperties properties;

    private Long createCourt() {
        SurfaceTypeResponse surface = Objects.requireNonNull(admin().postForEntity("/api/surface-types",
                new SurfaceTypeRequest("Clay", new BigDecimal("2.00")), SurfaceTypeResponse.class).getBody());
        return Objects.requireNonNull(admin().postForEntity(COURTS, new CourtRequest(1, "Centre", surface.id()),
                CourtResponse.class).getBody()).id();
    }

    private ResponseEntity<ProblemDetail> getCourtsAsProblem(TestRestTemplate client) {
        return client.getForEntity(COURTS, ProblemDetail.class);
    }

    @Test
    void should_returnTokensInHeaderAndBody_when_loginWithBasic() {
        ResponseEntity<TokenResponse> response = rest.withBasicAuth(ADMIN_PHONE, ADMIN_PASSWORD)
                .postForEntity(LOGIN_PATH, null, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse body = Objects.requireNonNull(response.getBody());
        assertThat(response.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + body.accessToken());
        assertThat(body.accessExpiresAt())
                .isEqualTo(MutableClock.DEFAULT_NOW.plus(properties.security().jwt().accessTokenValidity()));
        assertThat(body.refreshExpiresAt())
                .isEqualTo(MutableClock.DEFAULT_NOW.plus(properties.security().jwt().refreshTokenValidity()));
        assertThat(body.refreshToken()).isNotEqualTo(body.accessToken());
    }

    @Test
    void should_return401Problem_when_loginWithWrongPassword() {
        ResponseEntity<ProblemDetail> wrong = rest.withBasicAuth(ADMIN_PHONE, "nope")
                .postForEntity(LOGIN_PATH, null, ProblemDetail.class);

        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(wrong.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Basic");
        assertThat(Objects.requireNonNull(wrong.getBody()).getDetail()).isEqualTo("Invalid credentials");
    }

    @Test
    void should_returnSame401Problem_when_loginWithUnknownPhone() {
        ResponseEntity<ProblemDetail> unknown = rest.withBasicAuth("+421999999999", "nope")
                .postForEntity(LOGIN_PATH, null, ProblemDetail.class);

        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(Objects.requireNonNull(unknown.getBody()).getDetail())
                .as("unknown and known accounts must be indistinguishable").isEqualTo("Invalid credentials");
    }

    @Test
    void should_login_when_phoneContainsSpaces() {
        ResponseEntity<TokenResponse> spaced = rest.withBasicAuth("+421 900 000 900", ADMIN_PASSWORD)
                .postForEntity(LOGIN_PATH, null, TokenResponse.class);

        assertThat(spaced.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_allowHead_when_userRole() {
        ResponseEntity<Void> head = user().exchange("/api/courts", HttpMethod.HEAD, null, Void.class);

        assertThat(head.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_sendBearerChallengeAndDenyFrames_when_apiCalledWithoutToken() {
        ResponseEntity<ProblemDetail> response = rest.getForEntity("/api/courts", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void should_requireAuthentication_when_h2ConsoleDisabled() {
        ResponseEntity<ProblemDetail> response = rest.getForEntity("/h2-console/", ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return401Problem_when_customerWithoutPasswordLogsIn() {
        createCourt();
        ResponseEntity<ReservationResponse> reservation = user().postForEntity("/api/reservations",
                new CreateReservationRequest(1, MutableClock.DEFAULT_NOW.plus(Duration.ofDays(1)),
                        MutableClock.DEFAULT_NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)),
                        GameTypeDto.SINGLES, "+421900000555", "Customer"), ReservationResponse.class);
        assertThat(reservation.getStatusCode()).as("USER may create reservations").isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ProblemDetail> login = rest.withBasicAuth("+421900000555", "anything")
                .postForEntity(LOGIN_PATH, null, ProblemDetail.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(Objects.requireNonNull(login.getBody()).getDetail()).isEqualTo("Invalid credentials");
    }

    @Test
    void should_return401Problem_when_noToken() {
        ResponseEntity<ProblemDetail> response = getCourtsAsProblem(rest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = Objects.requireNonNull(response.getBody());
        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getTitle()).isEqualTo("Unauthorized");
        assertThat(problem.getDetail()).isEqualTo("Authentication is required");
        assertThat(problem.getInstance()).hasPath(COURTS);
    }

    @Test
    void should_return401Problem_when_tokenGarbage() {
        ResponseEntity<ProblemDetail> response = getCourtsAsProblem(bearer("garbage"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail()).isEqualTo("Invalid or expired token");
    }

    @Test
    void should_allowReadsForBothRoles_when_tokenValid() {
        assertThat(admin().getForEntity(COURTS, CourtResponse[].class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user().getForEntity(COURTS, CourtResponse[].class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return403Problem_when_userRoleDeletesCourt() {
        Long courtId = createCourt();

        ResponseEntity<ProblemDetail> response =
                user().exchange(COURTS + "/" + courtId, HttpMethod.DELETE, null, ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        ProblemDetail problem = Objects.requireNonNull(response.getBody());
        assertThat(problem.getTitle()).isEqualTo("Forbidden");
        assertThat(problem.getDetail()).isEqualTo("Insufficient role for this operation");
        assertThat(admin().getForEntity(COURTS + "/" + courtId, CourtResponse.class).getStatusCode())
                .as("court still there").isEqualTo(HttpStatus.OK);
        assertThat(user().postForEntity("/api/surface-types", new SurfaceTypeRequest("Grass", BigDecimal.ONE),
                ProblemDetail.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void should_return401Problem_when_accessTokenExpired() {
        clock.advance(properties.security().jwt().accessTokenValidity().plusSeconds(1));

        ResponseEntity<ProblemDetail> response = getCourtsAsProblem(admin());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail()).isEqualTo("Invalid or expired token");
    }

    @Test
    void should_issueWorkingAccessToken_when_refreshed() {
        TokenResponse original = login(USER_PHONE, USER_PASSWORD);
        clock.advance(Duration.ofMinutes(1));

        ResponseEntity<TokenResponse> refreshed =
                rest.postForEntity(REFRESH, new RefreshTokenRequest(original.refreshToken()), TokenResponse.class);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse body = Objects.requireNonNull(refreshed.getBody());
        assertThat(refreshed.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + body.accessToken());
        assertThat(body.accessToken()).isNotEqualTo(original.accessToken());
        assertThat(body.accessExpiresAt()).isEqualTo(original.accessExpiresAt().plus(Duration.ofMinutes(1)));
        assertThat(bearer(body.accessToken()).getForEntity(COURTS, CourtResponse[].class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return401Problem_when_refreshWithAccessToken() {
        TokenResponse tokens = login(USER_PHONE, USER_PASSWORD);

        ResponseEntity<ProblemDetail> response =
                rest.postForEntity(REFRESH, new RefreshTokenRequest(tokens.accessToken()), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(response.getBody()).getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getDetail()).isEqualTo("Token is not a refresh token");
    }

    @Test
    void should_return401Problem_when_refreshTokenExpired() {
        TokenResponse tokens = login(USER_PHONE, USER_PASSWORD);
        clock.advance(properties.security().jwt().refreshTokenValidity().plusSeconds(1));

        ResponseEntity<ProblemDetail> response =
                rest.postForEntity(REFRESH, new RefreshTokenRequest(tokens.refreshToken()), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail()).isEqualTo("Refresh token is invalid or expired");
    }

    @Test
    void should_return401Problem_when_refreshTokenUsedAsBearer() {
        TokenResponse tokens = login(USER_PHONE, USER_PASSWORD);

        ResponseEntity<ProblemDetail> response = getCourtsAsProblem(bearer(tokens.refreshToken()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(Objects.requireNonNull(response.getBody()).getDetail()).isEqualTo("Invalid or expired token");
    }

    @Test
    void should_return400Problem_when_refreshBodyBlank() {
        ResponseEntity<ProblemDetail> response =
                rest.postForEntity(REFRESH, new RefreshTokenRequest(""), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(response.getBody()).getProperties()).containsKey("errors");
    }

    @Test
    void should_exposeOpenApiWithoutToken_when_docsRequested() {
        ResponseEntity<String> docs = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody()).contains("\"bearerAuth\"").contains("\"basicAuth\"").contains("/api/auth/login");
        // the client follows the redirect to /swagger-ui/index.html; either way no 401
        assertThat(rest.getForEntity("/swagger-ui.html", String.class).getStatusCode())
                .isIn(HttpStatus.OK, HttpStatus.FOUND);
    }
}
