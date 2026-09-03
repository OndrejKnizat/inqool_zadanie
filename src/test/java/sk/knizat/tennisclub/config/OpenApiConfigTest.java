package sk.knizat.tennisclub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void should_declareBearerAndBasicSchemes_when_openApiBuilt() {
        OpenAPI openAPI = new OpenApiConfig().openAPI();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Tennis Club Reservations API");
        assertThat(openAPI.getInfo().getDescription())
                .isEqualTo(OpenApiConfig.DESCRIPTION)
                .contains("/api/auth/login", "ISO-8601", "1.5 for doubles");
        SecurityScheme bearer = openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH);
        assertThat(bearer.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearer.getScheme()).isEqualTo("bearer");
        assertThat(bearer.getBearerFormat()).isEqualTo("JWT");
        SecurityScheme basic = openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BASIC_AUTH);
        assertThat(basic.getScheme()).isEqualTo("basic");
        assertThat(openAPI.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey(OpenApiConfig.BEARER_AUTH));
    }
}
