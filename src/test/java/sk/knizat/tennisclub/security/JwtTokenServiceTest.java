package sk.knizat.tennisclub.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import sk.knizat.tennisclub.config.AppProperties;
import sk.knizat.tennisclub.config.JwtConfig;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.exception.UnauthorizedException;
import sk.knizat.tennisclub.support.MutableClock;
import sk.knizat.tennisclub.support.TestProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spring-free: encoder and decoder are built by {@link JwtConfig} from a test secret and a controllable clock. */
class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final AuthUser ADMIN = new AuthUser(1L, "+421900000001", "hash", RoleDto.ADMIN);

    private final MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
    private final AppProperties properties = TestProperties.defaults();
    private JwtDecoder decoder;
    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig(properties, clock);
        decoder = config.jwtDecoder();
        service = new JwtTokenService(config.jwtEncoder(), decoder, clock, properties);
    }

    @Test
    void should_issueAccessTokenWithClaims_when_issue() {
        TokenPair pair = service.issue(ADMIN);

        Jwt access = decoder.decode(pair.accessToken());
        assertThat(access.getSubject()).isEqualTo("+421900000001");
        assertThat(access.getClaimAsString(JwtClaimNames.ISS)).isEqualTo(JwtTokenService.ISSUER);
        assertThat(access.getClaimAsString(JwtTokenService.CLAIM_ROLE)).isEqualTo("ADMIN");
        assertThat(access.getClaimAsString(JwtTokenService.CLAIM_TYPE)).isEqualTo(JwtTokenService.TYPE_ACCESS);
        assertThat(access.getIssuedAt()).isEqualTo(NOW);
        assertThat(access.getExpiresAt()).isEqualTo(NOW.plus(TestProperties.ACCESS_VALIDITY));
        assertThat(pair.accessExpiresAt()).isEqualTo(NOW.plus(TestProperties.ACCESS_VALIDITY));
    }

    @Test
    void should_issueRefreshTokenWithLongerValidity_when_issue() {
        TokenPair pair = service.issue(ADMIN);

        Jwt refresh = decoder.decode(pair.refreshToken());
        assertThat(refresh.getSubject()).isEqualTo("+421900000001");
        assertThat(refresh.getClaimAsString(JwtTokenService.CLAIM_TYPE)).isEqualTo(JwtTokenService.TYPE_REFRESH);
        assertThat(refresh.getExpiresAt()).isEqualTo(NOW.plus(TestProperties.REFRESH_VALIDITY));
        assertThat(pair.refreshExpiresAt()).isEqualTo(NOW.plus(TestProperties.REFRESH_VALIDITY));
        assertThat(pair.refreshToken()).isNotEqualTo(pair.accessToken());
    }

    @Test
    void should_decodeRefreshToken_when_validAndNotExpired() {
        TokenPair pair = service.issue(ADMIN);
        clock.advance(TestProperties.REFRESH_VALIDITY.minusSeconds(1));

        Jwt jwt = service.decodeRefreshToken(pair.refreshToken());

        assertThat(jwt.getSubject()).isEqualTo("+421900000001");
        assertThat(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE)).isEqualTo("ADMIN");
    }

    @Test
    void should_rejectAccessToken_when_clockPassesAccessValidity() {
        TokenPair pair = service.issue(ADMIN);
        clock.advance(TestProperties.ACCESS_VALIDITY.plusSeconds(1));

        assertThatThrownBy(() -> decoder.decode(pair.accessToken()))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void should_rejectRefreshToken_when_clockPassesRefreshValidity() {
        TokenPair pair = service.issue(ADMIN);
        clock.advance(TestProperties.REFRESH_VALIDITY.plus(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> service.decodeRefreshToken(pair.refreshToken()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    @Test
    void should_rejectAccessToken_when_usedAsRefreshToken() {
        TokenPair pair = service.issue(ADMIN);

        assertThatThrownBy(() -> service.decodeRefreshToken(pair.accessToken()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Token is not a refresh token");
    }

    @Test
    void should_rejectToken_when_payloadTampered() {
        TokenPair pair = service.issue(ADMIN);
        String[] parts = pair.refreshToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"ADMIN\"", "\"USER\"").getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> service.decodeRefreshToken(tampered))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    @Test
    void should_rejectToken_when_signedWithDifferentSecret() {
        JwtConfig other = new JwtConfig(TestProperties.withSecret("another-secret-another-secret-another-secret"), clock);
        JwtTokenService otherService = new JwtTokenService(other.jwtEncoder(), other.jwtDecoder(), clock, properties);
        TokenPair pair = otherService.issue(ADMIN);

        assertThatThrownBy(() -> service.decodeRefreshToken(pair.refreshToken()))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> decoder.decode(pair.accessToken())).isInstanceOf(JwtException.class);
    }

    @Test
    void should_rejectToken_when_malformed() {
        assertThatThrownBy(() -> service.decodeRefreshToken("not-a-jwt"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token is invalid or expired");
    }
}
