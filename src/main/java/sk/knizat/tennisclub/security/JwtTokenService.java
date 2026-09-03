package sk.knizat.tennisclub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.config.AppProperties;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.exception.UnauthorizedException;

import java.time.Clock;
import java.time.Instant;

/**
 * Issues and validates the application's HS256 tokens (ARCHITECTURE.md O-11).
 * <p>
 * Claims: {@code iss} = {@value #ISSUER}, {@code sub} = phone number, {@code role}, {@code type}
 * ({@value #TYPE_ACCESS} or {@value #TYPE_REFRESH}), {@code iat} and {@code exp} computed from the application
 * {@link Clock} and the configured validity. The decoder (see {@code config.JwtConfig}) verifies the signature,
 * the issuer and the expiry against the same clock; this class additionally checks the token type.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenService {

    public static final String ISSUER = "tennis-club";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;
    private final AppProperties properties;

    /** Issues a fresh access/refresh pair for the user, both starting now. */
    public TokenPair issue(AuthUser user) {
        Instant now = clock.instant();
        Instant accessExpiry = now.plus(properties.security().jwt().accessTokenValidity());
        Instant refreshExpiry = now.plus(properties.security().jwt().refreshTokenValidity());
        return new TokenPair(
                encode(user, TYPE_ACCESS, now, accessExpiry),
                encode(user, TYPE_REFRESH, now, refreshExpiry),
                accessExpiry,
                refreshExpiry);
    }

    /**
     * Decodes and validates a refresh token.
     *
     * @throws UnauthorizedException when the token is malformed, has a bad signature, is expired, or is not a
     *                               refresh token
     */
    public Jwt decodeRefreshToken(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException ex) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }
        if (!TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TYPE))) {
            throw new UnauthorizedException("Token is not a refresh token");
        }
        return jwt;
    }

    private String encode(AuthUser user, String type, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.phoneNumber())
                .claim(CLAIM_ROLE, user.role().name())
                .claim(CLAIM_TYPE, type)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
