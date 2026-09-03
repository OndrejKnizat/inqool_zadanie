package sk.knizat.tennisclub.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.auth.RefreshTokenRequest;
import sk.knizat.tennisclub.dto.auth.TokenResponse;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.exception.UnauthorizedException;
import sk.knizat.tennisclub.security.JwtTokenService;
import sk.knizat.tennisclub.security.TokenPair;
import sk.knizat.tennisclub.service.UserService;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String PHONE = "+421900000001";
    private static final Instant T1 = Instant.parse("2026-06-01T10:15:00Z");
    private static final Instant T2 = Instant.parse("2026-06-08T10:00:00Z");
    private static final TokenPair PAIR = new TokenPair("access", "refresh", T1, T2);
    private static final AuthUser ADMIN = new AuthUser(1L, PHONE, "hash", RoleDto.ADMIN);

    @Mock
    private UserService userService;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthServiceImpl service;

    private static Jwt refreshJwt(String subject) {
        return Jwt.withTokenValue("refresh").header("alg", "HS256").subject(subject)
                .claim("type", "refresh").claim("role", "USER").build();
    }

    @Test
    void should_issuePairForUser_when_issueTokens() {
        when(userService.findAuthUserByPhoneNumber(PHONE)).thenReturn(Optional.of(ADMIN));
        when(jwtTokenService.issue(any())).thenReturn(PAIR);

        TokenResponse response = service.issueTokens(PHONE);

        assertThat(response).isEqualTo(new TokenResponse("access", "refresh", T1, T2));
        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(jwtTokenService).issue(captor.capture());
        assertThat(captor.getValue().phoneNumber()).isEqualTo(PHONE);
        assertThat(captor.getValue().role()).isEqualTo(RoleDto.ADMIN);
    }

    @Test
    void should_throwUnauthorized_when_issueTokensForUnknownUser() {
        when(userService.findAuthUserByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueTokens(PHONE))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account does not exist or cannot log in");
        verify(jwtTokenService, never()).issue(any());
    }

    @Test
    void should_throwUnauthorized_when_issueTokensForUserWithoutPassword() {
        when(userService.findAuthUserByPhoneNumber(PHONE))
                .thenReturn(Optional.of(new AuthUser(2L, PHONE, null, RoleDto.USER)));

        assertThatThrownBy(() -> service.issueTokens(PHONE)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void should_issueNewPairForTokenSubject_when_refreshValid() {
        when(jwtTokenService.decodeRefreshToken("refresh")).thenReturn(refreshJwt(PHONE));
        when(userService.findAuthUserByPhoneNumber(PHONE)).thenReturn(Optional.of(ADMIN));
        when(jwtTokenService.issue(any())).thenReturn(PAIR);

        TokenResponse response = service.refresh(new RefreshTokenRequest("refresh"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshExpiresAt()).isEqualTo(T2);
        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(jwtTokenService).issue(captor.capture());
        assertThat(captor.getValue().role()).as("current role, not the one in the old token").isEqualTo(RoleDto.ADMIN);
    }

    @Test
    void should_propagateUnauthorized_when_refreshTokenInvalid() {
        when(jwtTokenService.decodeRefreshToken("bad")).thenThrow(new UnauthorizedException("invalid"));

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("bad")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("invalid");
        verify(userService, never()).findAuthUserByPhoneNumber(any());
    }

    @Test
    void should_throwUnauthorized_when_refreshUserDeletedMeanwhile() {
        when(jwtTokenService.decodeRefreshToken("refresh")).thenReturn(refreshJwt(PHONE));
        when(userService.findAuthUserByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(new RefreshTokenRequest("refresh")))
                .isInstanceOf(UnauthorizedException.class);
        verify(jwtTokenService, never()).issue(any());
    }
}
