package sk.knizat.tennisclub.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload of {@code POST /api/auth/refresh}.
 *
 * @param refreshToken refresh token issued by a previous login or refresh
 */
public record RefreshTokenRequest(@NotBlank String refreshToken) {
}
