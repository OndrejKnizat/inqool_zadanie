package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** With the H2 console enabled (dev profile) it is public and API responses allow same-origin frames. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.h2.console.enabled=true")
class H2ConsoleSecurityTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void should_exposeConsoleWithoutToken_when_h2ConsoleEnabled() {
        ResponseEntity<String> console = rest.getForEntity("/h2-console/", String.class);

        assertThat(console.getStatusCode()).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void should_allowSameOriginFrames_when_h2ConsoleEnabled() {
        ResponseEntity<String> api = rest.getForEntity("/api/courts", String.class);

        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(api.getHeaders().getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
    }
}
