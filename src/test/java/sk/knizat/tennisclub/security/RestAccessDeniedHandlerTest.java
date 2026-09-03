package sk.knizat.tennisclub.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class RestAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(objectMapper);

    @Test
    void should_writeForbiddenProblem_when_accessDenied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/courts/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        JsonNode problem = objectMapper.readTree(response.getContentAsString());
        assertThat(problem.get("status").asInt()).isEqualTo(403);
        assertThat(problem.get("title").asText()).isEqualTo("Forbidden");
        assertThat(problem.get("detail").asText()).isEqualTo("Insufficient role for this operation");
        assertThat(problem.get("instance").asText()).isEqualTo("/api/courts/1");
    }
}
