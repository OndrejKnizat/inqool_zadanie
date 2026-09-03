package sk.knizat.tennisclub.service;

import sk.knizat.tennisclub.dto.auth.RefreshTokenRequest;
import sk.knizat.tennisclub.dto.auth.TokenResponse;

/** Token issuing for the auth endpoints (ARCHITECTURE.md section 5.1). */
public interface AuthService {

    /**
     * Issues an access/refresh token pair for an already authenticated user.
     *
     * @param phoneNumber login name of the authenticated user
     * @throws sk.knizat.tennisclub.exception.UnauthorizedException when the account no longer exists or cannot log in
     */
    TokenResponse issueTokens(String phoneNumber);

    /**
     * Validates a refresh token and issues a new token pair for its user.
     *
     * @throws sk.knizat.tennisclub.exception.UnauthorizedException when the token is invalid, expired, not a refresh
     *                                                                token, or its user cannot log in any more
     */
    TokenResponse refresh(RefreshTokenRequest request);
}
