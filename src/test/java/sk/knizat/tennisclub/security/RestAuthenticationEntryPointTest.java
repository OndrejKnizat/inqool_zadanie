package sk.knizat.tennisclub.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);

    private JsonNode commence(AuthenticationException exception) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, exception);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        return objectMapper.readTree(response.getContentAsString());
    }

    @Test
    void should_writeProblemWithGenericDetail_when_tokenInvalid() throws Exception {
        JsonNode problem = commence(new InvalidBearerTokenException("signature"));

        assertThat(problem.get("status").asInt()).isEqualTo(401);
        assertThat(problem.get("title").asText()).isEqualTo("Unauthorized");
        assertThat(problem.get("detail").asText()).isEqualTo("Invalid or expired token");
        assertThat(problem.get("instance").asText()).isEqualTo("/api/courts");
    }

    @Test
    void should_writeAuthenticationRequired_when_credentialsMissing() throws Exception {
        JsonNode problem = commence(new InsufficientAuthenticationException("none"));

        assertThat(problem.get("detail").asText()).isEqualTo("Authentication is required");
    }

    @Test
    void should_writeInvalidCredentials_when_badCredentials() throws Exception {
        JsonNode problem = commence(new BadCredentialsException("wrong"));

        assertThat(problem.get("detail").asText()).isEqualTo("Invalid credentials");
    }

    @Test
    void should_writeCannotLogIn_when_accountDisabled() throws Exception {
        JsonNode problem = commence(new DisabledException("no password"));

        assertThat(problem.get("detail").asText()).as("no user enumeration").isEqualTo("Invalid credentials");
    }

    @Test
    void should_stillWriteProblem_when_requestUriIsNotAValidUri() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/courts/a b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("wrong"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"status\":401").doesNotContain("instance");
    }
}
