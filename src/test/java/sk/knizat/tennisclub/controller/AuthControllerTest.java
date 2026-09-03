package sk.knizat.tennisclub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.auth.RefreshTokenRequest;
import sk.knizat.tennisclub.dto.auth.TokenResponse;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.exception.UnauthorizedException;
import sk.knizat.tennisclub.security.AppUserDetails;
import sk.knizat.tennisclub.service.AuthService;
import sk.knizat.tennisclub.support.AbstractWebMvcTest;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends AbstractWebMvcTest {

    private static final String LOGIN = "/api/auth/login";
    private static final String REFRESH = "/api/auth/refresh";
    private static final String PHONE = "+421900000001";
    private static final String PASSWORD = "secret";
    private static final TokenResponse TOKENS = new TokenResponse("access-token", "refresh-token",
            Instant.parse("2026-06-01T10:15:00Z"), Instant.parse("2026-06-08T10:00:00Z"));

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AuthService authService;

    private void givenAccount(String passwordHash) {
        when(userDetailsService.loadUserByUsername(PHONE))
                .thenReturn(new AppUserDetails(new AuthUser(1L, PHONE, passwordHash, RoleDto.ADMIN)));
    }

    @Test
    void should_returnTokensInHeaderAndBody_when_loginWithValidBasicCredentials() throws Exception {
        givenAccount(passwordEncoder.encode(PASSWORD));
        when(authService.issueTokens(PHONE)).thenReturn(TOKENS);

        mockMvc.perform(post(LOGIN).with(httpBasic(PHONE, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.accessExpiresAt").value("2026-06-01T10:15:00Z"))
                .andExpect(jsonPath("$.refreshExpiresAt").value("2026-06-08T10:00:00Z"));
    }

    @Test
    void should_return401Problem_when_loginWithWrongPassword() throws Exception {
        givenAccount(passwordEncoder.encode(PASSWORD));

        mockMvc.perform(post(LOGIN).with(httpBasic(PHONE, "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"))
                .andExpect(header().doesNotExist(HttpHeaders.AUTHORIZATION));

        verify(authService, never()).issueTokens(any());
    }

    @Test
    void should_return401Problem_when_loginWithUnknownUser() throws Exception {
        when(userDetailsService.loadUserByUsername(PHONE)).thenThrow(new UsernameNotFoundException("no"));

        mockMvc.perform(post(LOGIN).with(httpBasic(PHONE, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void should_return401Problem_when_loginOfAccountWithoutPassword() throws Exception {
        givenAccount(null);

        mockMvc.perform(post(LOGIN).with(httpBasic(PHONE, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void should_return401Problem_when_loginWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(post(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Authentication is required"));
    }

    @Test
    void should_return401Problem_when_loginWithBearerInsteadOfBasic() throws Exception {
        mockMvc.perform(post(LOGIN).header(HttpHeaders.AUTHORIZATION, "Bearer whatever"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verify(jwtDecoder, never()).decode(any());
    }

    @Test
    void should_returnNewTokens_when_refreshValid() throws Exception {
        when(authService.refresh(new RefreshTokenRequest("refresh-token"))).thenReturn(TOKENS);

        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void should_return401Problem_when_refreshInvalid() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnauthorizedException("Refresh token is invalid or expired"));

        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Refresh token is invalid or expired"));
    }

    @Test
    void should_return400WithErrors_when_refreshTokenBlank() throws Exception {
        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.refreshToken").exists());

        verify(authService, never()).refresh(any());
    }

    @Test
    void should_return405_when_loginWithGet() throws Exception {
        mockMvc.perform(get(LOGIN))
                .andExpect(status().isMethodNotAllowed());
    }
}
