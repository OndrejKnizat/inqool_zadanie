package sk.knizat.tennisclub.controller;

import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import sk.knizat.tennisclub.config.SecurityConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the handler branches that the real controllers do not reach, through a probe controller
 * registered only in this slice.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandlerTest.ProbeController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_return400WithErrors_when_pathVariableViolatesConstraint() throws Exception {
        mockMvc.perform(get("/probe/0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    void should_return400Problem_when_requiredParameterMissing() throws Exception {
        mockMvc.perform(get("/probe/search"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Missing parameter"))
                .andExpect(jsonPath("$.detail").value("Required parameter 'q' is missing"));
    }

    @Test
    void should_return500Problem_when_unexpectedExceptionThrown() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void should_return405Problem_when_methodNotSupported() throws Exception {
        mockMvc.perform(post("/probe/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return415Problem_when_mediaTypeNotSupported() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return404Problem_when_pathUnknown() throws Exception {
        mockMvc.perform(get("/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_useFallbackMessage_when_fieldErrorHasNoDefaultMessage() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "name", null));
        binding.addError(new FieldError("request", "name", "second error is ignored"));
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("sample", String.class), 0);

        ResponseEntity<Object> response = new GlobalExceptionHandler().handleMethodArgumentNotValid(
                new MethodArgumentNotValidException(parameter, binding), new HttpHeaders(),
                HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getProperties()).containsEntry("errors", Map.of("name", "invalid value"));
    }

    @SuppressWarnings("unused")
    void sample(String name) {
        // signature only; used to build a MethodParameter
    }

    /** Minimal controller exercising the generic error paths. */
    @RestController
    static class ProbeController {

        @GetMapping("/probe/search")
        String search(@RequestParam String q) {
            return q;
        }

        @GetMapping("/probe/{id}")
        String byId(@PathVariable @Min(1) long id) {
            return String.valueOf(id);
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("boom");
        }

        @PostMapping(value = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        String create(@RequestBody Map<String, String> body) {
            return body.toString();
        }
    }
}
