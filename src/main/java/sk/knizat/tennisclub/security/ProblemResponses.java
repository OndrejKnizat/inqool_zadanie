package sk.knizat.tennisclub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;

/** Writes RFC 7807 problem responses from the security filters, where no controller advice is available. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProblemResponses {

    static void write(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        try {
            problem.setInstance(URI.create(request.getRequestURI()));
        } catch (IllegalArgumentException ex) {
            // a raw URI the container let through but java.net.URI rejects; the instance is optional
        }
        response.setStatus(status.value());
        // no charset parameter, so the header equals the one produced by the controller advice; Jackson writes UTF-8
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
