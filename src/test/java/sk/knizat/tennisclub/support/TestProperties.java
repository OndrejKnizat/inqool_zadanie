package sk.knizat.tennisclub.support;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import sk.knizat.tennisclub.config.AppProperties;

import java.time.Duration;

/** {@link AppProperties} instances for Spring-free unit tests. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestProperties {

    public static final String SECRET = "unit-test-secret-unit-test-secret-unit-test-secret";
    public static final Duration ACCESS_VALIDITY = Duration.ofMinutes(15);
    public static final Duration REFRESH_VALIDITY = Duration.ofDays(7);

    public static AppProperties withSecret(String secret) {
        return new AppProperties(
                new AppProperties.DataInit(false),
                new AppProperties.Security(
                        new AppProperties.Security.Jwt(secret, ACCESS_VALIDITY, REFRESH_VALIDITY),
                        new AppProperties.Security.Admin(true, "+420000000000", "Administrator", "admin")),
                new AppProperties.Reservation(Duration.ofMinutes(15), Duration.ofHours(4)));
    }

    public static AppProperties defaults() {
        return withSecret(SECRET);
    }
}
