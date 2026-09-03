package sk.knizat.tennisclub.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    @Test
    void should_buildStandardMessage_when_notFoundFactoryUsed() {
        assertThat(NotFoundException.of("SurfaceType", 7L)).hasMessage("SurfaceType with id 7 not found");
    }

    @Test
    void should_carryMessage_when_constructed() {
        assertThat(new ValidationException("bad")).hasMessage("bad").isInstanceOf(RuntimeException.class);
        assertThat(new ConflictException("clash")).hasMessage("clash").isInstanceOf(RuntimeException.class);
        assertThat(new UnauthorizedException("denied")).hasMessage("denied").isInstanceOf(RuntimeException.class);
    }
}
