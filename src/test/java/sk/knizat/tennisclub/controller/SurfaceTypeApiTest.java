package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeRequest;
import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;
import sk.knizat.tennisclub.support.AbstractApiTest;
import sk.knizat.tennisclub.support.MutableClock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end test through the real stack (controller, service, DAO, H2). Names are unique per test. */
class SurfaceTypeApiTest extends AbstractApiTest {

    private static final String BASE = "/api/surface-types";

    private static String unique(TestInfo info) {
        return "Surface " + info.getTestMethod().orElseThrow().getName();
    }

    private ResponseEntity<SurfaceTypeResponse> create(String name, String price) {
        return rest.postForEntity(BASE, new SurfaceTypeRequest(name, new BigDecimal(price)), SurfaceTypeResponse.class);
    }

    @Test
    void should_completeLifecycle_when_createGetListUpdateDelete(TestInfo info) {
        String name = unique(info);

        ResponseEntity<SurfaceTypeResponse> created = create("  " + name + "  ", "2.5");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SurfaceTypeResponse body = Objects.requireNonNull(created.getBody());
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo(name);
        assertThat(body.pricePerMinute()).isEqualByComparingTo("2.50");
        assertThat(body.createdAt()).isEqualTo(MutableClock.DEFAULT_NOW);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        assertThat(created.getHeaders().getLocation().getPath()).isEqualTo(BASE + "/" + body.id());

        ResponseEntity<SurfaceTypeResponse> fetched =
                rest.getForEntity(created.getHeaders().getLocation(), SurfaceTypeResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isEqualTo(body);

        SurfaceTypeResponse[] list = rest.getForObject(BASE, SurfaceTypeResponse[].class);
        assertThat(Arrays.stream(list).map(SurfaceTypeResponse::id)).contains(body.id());

        clock.advance(Duration.ofHours(1));
        ResponseEntity<SurfaceTypeResponse> updated = rest.exchange(BASE + "/" + body.id(), HttpMethod.PUT,
                new HttpEntity<>(new SurfaceTypeRequest(name + " v2", new BigDecimal("3"))),
                SurfaceTypeResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        SurfaceTypeResponse updatedBody = Objects.requireNonNull(updated.getBody());
        assertThat(updatedBody.name()).isEqualTo(name + " v2");
        assertThat(updatedBody.pricePerMinute()).isEqualByComparingTo("3.00");
        assertThat(updatedBody.createdAt()).isEqualTo(MutableClock.DEFAULT_NOW);
        assertThat(updatedBody.updatedAt()).isEqualTo(MutableClock.DEFAULT_NOW.plus(Duration.ofHours(1)));

        ResponseEntity<Void> deleted = rest.exchange(BASE + "/" + body.id(), HttpMethod.DELETE, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ProblemDetail> gone = rest.getForEntity(BASE + "/" + body.id(), ProblemDetail.class);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gone.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(gone.getBody()).getDetail())
                .isEqualTo("SurfaceType with id " + body.id() + " not found");

        SurfaceTypeResponse[] afterDelete = rest.getForObject(BASE, SurfaceTypeResponse[].class);
        assertThat(Arrays.stream(afterDelete).map(SurfaceTypeResponse::id)).doesNotContain(body.id());
    }

    @Test
    void should_return409_when_creatingDuplicateName(TestInfo info) {
        String name = unique(info);
        assertThat(create(name, "1").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ProblemDetail> duplicate =
                rest.postForEntity(BASE, new SurfaceTypeRequest(name, BigDecimal.ONE), ProblemDetail.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(Objects.requireNonNull(duplicate.getBody()).getTitle()).isEqualTo("Conflict");
    }

    @Test
    void should_allowReuseOfName_when_originalIsSoftDeleted(TestInfo info) {
        String name = unique(info);
        Long firstId = Objects.requireNonNull(create(name, "1").getBody()).id();
        rest.delete(BASE + "/" + firstId);

        ResponseEntity<SurfaceTypeResponse> second = create(name, "2");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(Objects.requireNonNull(second.getBody()).id()).isNotEqualTo(firstId);
    }

    @Test
    void should_return400WithErrors_when_bodyInvalid() {
        ResponseEntity<ProblemDetail> response =
                rest.postForEntity(BASE, new SurfaceTypeRequest("", null), ProblemDetail.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(response.getBody()).getProperties()).containsKey("errors");
    }
}
