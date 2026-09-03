package sk.knizat.tennisclub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Answers every authentication failure with a 401 {@code application/problem+json} body: missing
 * credentials, wrong Basic credentials, an account without a password, or a missing/invalid/expired bearer token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String TITLE = "Unauthorized";
    static final String LOGIN_PATH = "/api/auth/login";

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        String detail = detailOf(exception);
        log.debug("401 for {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challengeFor(request));
        ProblemResponses.write(objectMapper, request, response, HttpStatus.UNAUTHORIZED, TITLE, detail);
    }

    /** RFC 7235 challenge: Basic on the login endpoint, Bearer everywhere else. */
    static String challengeFor(HttpServletRequest request) {
        return LOGIN_PATH.equals(request.getRequestURI()) ? "Basic realm=\"tennis-club\"" : "Bearer";
    }

    /**
     * Short, non-leaking explanation per failure kind. Wrong password, unknown account and an account without
     * a password all read the same, so the endpoint cannot be used to find out whether a phone number is
     * registered.
     */
    static String detailOf(AuthenticationException exception) {
        if (exception instanceof BadCredentialsException || exception instanceof DisabledException) {
            return "Invalid credentials";
        }
        if (exception instanceof InsufficientAuthenticationException) {
            return "Authentication is required";
        }
        return "Invalid or expired token";
    }
}
