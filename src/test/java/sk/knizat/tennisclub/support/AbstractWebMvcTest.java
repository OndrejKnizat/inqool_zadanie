package sk.knizat.tennisclub.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sk.knizat.tennisclub.config.PasswordConfig;
import sk.knizat.tennisclub.config.SecurityConfig;
import sk.knizat.tennisclub.security.AccessTokenAuthenticationConverter;
import sk.knizat.tennisclub.security.RestAccessDeniedHandler;
import sk.knizat.tennisclub.security.RestAuthenticationEntryPoint;

/**
 * Common wiring of {@code @WebMvcTest} slices: the real {@link SecurityConfig} with its entry point, access
 * denied handler, token converter and password encoder, plus mocks for the two beans that would need the
 * database or the JWT secret. Requests are authenticated with {@code @WithMockUser(roles = ...)} (the
 * authorization rules only look at the granted roles) and {@code @WithAnonymousUser} for the 401 cases; the
 * {@link JwtDecoder} mock can be stubbed to simulate bearer tokens.
 */
@Import({SecurityConfig.class, PasswordConfig.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, AccessTokenAuthenticationConverter.class})
public abstract class AbstractWebMvcTest {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @MockitoBean
    protected UserDetailsService userDetailsService;
}
