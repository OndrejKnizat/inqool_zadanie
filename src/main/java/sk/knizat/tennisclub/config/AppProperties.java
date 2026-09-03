package sk.knizat.tennisclub.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Application configuration bound from the {@code app.*} prefix.
 *
 * @param dataInit data initialization switch
 * @param security security related settings (JWT, bootstrap admin)
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull @Valid DataInit dataInit,
        @NotNull @Valid Security security) {

    /**
     * Initial data switch: when enabled, default surface types and courts are created at startup.
     *
     * @param enabled whether initial data should be created
     */
    public record DataInit(boolean enabled) {
    }

    /**
     * Security settings.
     *
     * @param jwt JWT token settings
     * @param admin bootstrap administrator account settings
     */
    public record Security(
            @NotNull @Valid Jwt jwt,
            @NotNull @Valid Admin admin) {

        /**
         * JWT (HS256) settings.
         *
         * @param secret shared secret, at least 32 bytes
         * @param accessTokenValidity validity of access tokens
         * @param refreshTokenValidity validity of refresh tokens
         */
        public record Jwt(
                @NotBlank @Size(min = 32) String secret,
                @NotNull Duration accessTokenValidity,
                @NotNull Duration refreshTokenValidity) {
        }

        /**
         * Bootstrap ADMIN account created at startup when enabled.
         *
         * @param enabled whether the admin account should be created
         * @param phoneNumber admin login (phone number)
         * @param name admin display name
         * @param password admin raw password
         */
        public record Admin(
                boolean enabled,
                @NotBlank String phoneNumber,
                @NotBlank String name,
                @NotBlank String password) {
        }
    }
}
