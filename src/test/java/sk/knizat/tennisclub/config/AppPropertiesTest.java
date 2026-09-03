package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    private static final String[] VALID = {
            "app.data-init.enabled=true",
            "app.security.jwt.secret=0123456789012345678901234567890123",
            "app.security.jwt.access-token-validity=PT15M",
            "app.security.jwt.refresh-token-validity=P7D",
            "app.security.admin.enabled=true",
            "app.security.admin.phone-number=+420000000000",
            "app.security.admin.name=Administrator",
            "app.security.admin.password=admin",
            "app.reservation.min-duration=PT15M",
            "app.reservation.max-duration=PT4H"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void should_bindAllNestedProperties_when_configurationIsValid() {
        runner.withPropertyValues(VALID).run(ctx -> {
            AppProperties props = ctx.getBean(AppProperties.class);
            assertThat(props.dataInit().enabled()).isTrue();
            assertThat(props.security().jwt().secret()).isEqualTo("0123456789012345678901234567890123");
            assertThat(props.security().jwt().accessTokenValidity()).isEqualTo(Duration.ofMinutes(15));
            assertThat(props.security().jwt().refreshTokenValidity()).isEqualTo(Duration.ofDays(7));
            assertThat(props.security().admin().enabled()).isTrue();
            assertThat(props.security().admin().phoneNumber()).isEqualTo("+420000000000");
            assertThat(props.security().admin().name()).isEqualTo("Administrator");
            assertThat(props.security().admin().password()).isEqualTo("admin");
            assertThat(props.reservation().minDuration()).isEqualTo(Duration.ofMinutes(15));
            assertThat(props.reservation().maxDuration()).isEqualTo(Duration.ofHours(4));
        });
    }

    @Test
    void should_failStartup_when_jwtSecretIsShorterThan32Characters() {
        runner.withPropertyValues(VALID)
                .withPropertyValues("app.security.jwt.secret=too-short")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(BindValidationException.class));
    }

    @Test
    void should_failStartup_when_nestedSectionIsMissing() {
        runner.withPropertyValues(
                        "app.security.jwt.secret=0123456789012345678901234567890123",
                        "app.security.jwt.access-token-validity=PT15M",
                        "app.security.jwt.refresh-token-validity=P7D")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(BindValidationException.class));
    }

    @Test
    void should_failStartup_when_reservationDurationIsMissing() {
        runner.withPropertyValues(VALID)
                .withPropertyValues("app.reservation.max-duration=")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(BindValidationException.class));
    }

    @Configuration
    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesConfig {
    }
}
