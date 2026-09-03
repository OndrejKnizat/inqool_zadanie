package sk.knizat.tennisclub.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sk.knizat.tennisclub.dto.auth.RefreshTokenRequest;
import sk.knizat.tennisclub.dto.auth.TokenResponse;
import sk.knizat.tennisclub.service.AuthService;

import java.security.Principal;

/**
 * Authentication endpoints ({@code /api/auth}). Login is authenticated by the HTTP Basic filter chain before
 * this controller runs; both endpoints return the access token in the {@code Authorization} header and in the body.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login and token refresh")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Log in with HTTP Basic (phone number + password) and receive tokens",
            security = @SecurityRequirement(name = "basicAuth"))
    @ApiResponse(responseCode = "200", description = "Tokens issued; the access token is also in the header",
            headers = @Header(name = HttpHeaders.AUTHORIZATION, description = "Bearer <access token>",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "401", description = "Wrong credentials, unknown phone number or an account "
            + "without a password (application/problem+json)")
    public ResponseEntity<TokenResponse> login(Principal principal) {
        return withBearerHeader(authService.issueTokens(principal.getName()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new token pair", security = {})
    @ApiResponse(responseCode = "200", description = "New token pair; the access token is also in the header",
            headers = @Header(name = HttpHeaders.AUTHORIZATION, description = "Bearer <access token>",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token, or an access token "
            + "sent instead (application/problem+json)")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return withBearerHeader(authService.refresh(request));
    }

    private static ResponseEntity<TokenResponse> withBearerHeader(TokenResponse tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + tokens.accessToken())
                .body(tokens);
    }
}
