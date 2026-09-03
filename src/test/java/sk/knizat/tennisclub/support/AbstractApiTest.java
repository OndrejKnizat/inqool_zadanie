package sk.knizat.tennisclub.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.auth.TokenResponse;
import sk.knizat.tennisclub.service.UserService;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base of end-to-end API tests: real HTTP stack on a random port, {@code test} profile, controllable clock.
 * Every test starts with a reset clock, a fresh ADMIN and USER account (created through the service layer and
 * logged in through {@code POST /api/auth/login}), and ends with a cleaned database (FK-safe order), keeping
 * the classes that share this cached context independent. {@link #admin()} and {@link #user()} are clients
 * carrying the respective bearer token; {@link #rest} is anonymous.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractApiTest {

    protected static final String LOGIN_PATH = "/api/auth/login";
    protected static final String ADMIN_PHONE = "+421900000900";
    protected static final String ADMIN_PASSWORD = "admin-secret";
    protected static final String USER_PHONE = "+421900000901";
    protected static final String USER_PASSWORD = "user-secret";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserService userService;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    private TestRestTemplate admin;
    private TestRestTemplate user;

    @BeforeEach
    void resetClockAndLogin() {
        clock.reset();
        userService.createAccount(ADMIN_PHONE, "Admin", ADMIN_PASSWORD, RoleDto.ADMIN);
        userService.createAccount(USER_PHONE, "User", USER_PASSWORD, RoleDto.USER);
        admin = bearer(login(ADMIN_PHONE, ADMIN_PASSWORD).accessToken());
        user = bearer(login(USER_PHONE, USER_PASSWORD).accessToken());
    }

    @AfterEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    /** Client authenticated as the per-test ADMIN account. */
    protected TestRestTemplate admin() {
        return admin;
    }

    /** Client authenticated as the per-test USER account. */
    protected TestRestTemplate user() {
        return user;
    }

    /** Logs in through the API with HTTP Basic and returns the issued tokens (asserting a 200). */
    protected TokenResponse login(String phoneNumber, String password) {
        ResponseEntity<TokenResponse> response =
                rest.withBasicAuth(phoneNumber, password).postForEntity(LOGIN_PATH, null, TokenResponse.class);
        assertThat(response.getStatusCode()).as("login of " + phoneNumber).isEqualTo(HttpStatus.OK);
        return Objects.requireNonNull(response.getBody());
    }

    /** A client that sends the given bearer token with every request. */
    protected TestRestTemplate bearer(String accessToken) {
        TestRestTemplate client = new TestRestTemplate(
                restTemplateBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
        client.setUriTemplateHandler(rest.getRestTemplate().getUriTemplateHandler());
        return client;
    }
}
