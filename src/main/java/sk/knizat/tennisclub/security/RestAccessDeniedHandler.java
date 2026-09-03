package sk.knizat.tennisclub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Answers an authenticated request that lacks the required role with a 403 {@code application/problem+json}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    static final String TITLE = "Forbidden";
    static final String DETAIL = "Insufficient role for this operation";

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        log.debug("403 for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        ProblemResponses.write(objectMapper, request, response, HttpStatus.FORBIDDEN, TITLE, DETAIL);
    }
}
