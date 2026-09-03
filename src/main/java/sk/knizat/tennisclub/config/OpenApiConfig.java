package sk.knizat.tennisclub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata: API info and the two security schemes, {@value #BEARER_AUTH} (JWT access token, applied
 * globally) and {@value #BASIC_AUTH} (login only), so Swagger UI can authorise requests.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String BASIC_AUTH = "basicAuth";

    static final String DESCRIPTION = """
            Surface types (code list with a price per minute), courts, court reservations with price calculation \
            and user accounts of a tennis club. All endpoints live under `/api`; errors are RFC 7807 problem \
            details (`application/problem+json`): 400 validation (including an overlapping reservation), \
            401 missing/invalid/expired token, 403 insufficient role, 404 unknown or soft-deleted resource, \
            409 state conflict (duplicate business key, resource still in use).

            **Authentication:** `POST /api/auth/login` with HTTP Basic (username = phone number, password) returns \
            an access token in the `Authorization: Bearer ...` response header and in the body together with a \
            refresh token; `POST /api/auth/refresh` exchanges the refresh token for a new pair. Every other \
            endpoint needs `Authorization: Bearer <access token>`. Roles: `USER` may read everything except other \
            users' accounts (`/api/users/me` only) and may create reservations; `ADMIN` may do everything.

            **Conventions:** times are ISO-8601 instants in UTC (e.g. `2026-09-10T10:00:00Z`) and reservation \
            intervals are half-open `[startTime, endTime)` on whole minutes; phone numbers are normalised \
            (spaces and hyphens removed) and must match `^\\+?[0-9]{7,15}$`; prices are decimals with two places: \
            `minutes x pricePerMinute`, multiplied by 1.5 for doubles, rounded HALF_UP, and stored as a snapshot \
            on the reservation. Delete operations are soft deletes.""";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tennis Club Reservations API")
                        .version("v1")
                        .description(DESCRIPTION))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(BASIC_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
