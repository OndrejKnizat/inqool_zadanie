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

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tennis Club Reservations API")
                        .version("v1")
                        .description("Surface types, courts, reservations and user accounts of a tennis club. "
                                + "Log in with HTTP Basic (phone number + password) on POST /api/auth/login and "
                                + "use the returned access token as a bearer token everywhere else."))
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
