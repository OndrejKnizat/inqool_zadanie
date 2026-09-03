package sk.knizat.tennisclub.security;

import java.time.Instant;

/**
 * Access and refresh token issued together.
 *
 * @param accessToken      serialised access JWT
 * @param refreshToken     serialised refresh JWT
 * @param accessExpiresAt  expiry of the access token
 * @param refreshExpiresAt expiry of the refresh token
 */
public record TokenPair(String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
}
