package sk.knizat.tennisclub.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import sk.knizat.tennisclub.dto.auth.RefreshTokenRequest;
import sk.knizat.tennisclub.dto.auth.TokenResponse;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.exception.UnauthorizedException;
import sk.knizat.tennisclub.security.JwtTokenService;
import sk.knizat.tennisclub.security.TokenPair;
import sk.knizat.tennisclub.service.AuthService;
import sk.knizat.tennisclub.service.UserService;

/**
 * Default {@link AuthService}. Tokens are stateless, so a refresh re-loads the user to make sure the account
 * still exists, is not soft-deleted and still has a password; the role in the new tokens is the current one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    @Override
    public TokenResponse issueTokens(String phoneNumber) {
        TokenResponse tokens = issueFor(loadLoginAccount(phoneNumber));
        log.debug("Issued tokens for user {}", phoneNumber);
        return tokens;
    }

    @Override
    public TokenResponse refresh(RefreshTokenRequest request) {
        Jwt jwt = jwtTokenService.decodeRefreshToken(request.refreshToken());
        TokenResponse tokens = issueFor(loadLoginAccount(jwt.getSubject()));
        log.debug("Refreshed tokens for user {}", jwt.getSubject());
        return tokens;
    }

    private AuthUser loadLoginAccount(String phoneNumber) {
        return userService.findAuthUserByPhoneNumber(phoneNumber)
                .filter(AuthUser::canLogin)
                .orElseThrow(() -> new UnauthorizedException("Account does not exist or cannot log in"));
    }

    private TokenResponse issueFor(AuthUser user) {
        TokenPair pair = jwtTokenService.issue(user);
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.accessExpiresAt(),
                pair.refreshExpiresAt());
    }
}
