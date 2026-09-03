package sk.knizat.tennisclub.dto.auth;

import java.time.Instant;

/**
 * Tokens issued by login or refresh. The access token is additionally returned in the
 * {@code Authorization: Bearer} response header.
 *
 * @param accessToken      short-lived JWT for the {@code Authorization} header
 * @param refreshToken     long-lived JWT accepted only by {@code POST /api/auth/refresh}
 * @param accessExpiresAt  expiry of the access token
 * @param refreshExpiresAt expiry of the refresh token
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        Instant accessExpiresAt,
        Instant refreshExpiresAt) {
}
