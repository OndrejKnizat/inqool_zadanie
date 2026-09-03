package sk.knizat.tennisclub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import sk.knizat.tennisclub.dto.RoleDto;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import sk.knizat.tennisclub.security.AccessTokenAuthenticationConverter;
import sk.knizat.tennisclub.security.RestAccessDeniedHandler;
import sk.knizat.tennisclub.security.RestAuthenticationEntryPoint;

/**
 * Web security (ARCHITECTURE.md section 5.6): stateless, CSRF off, two filter chains.
 * <ol>
 * <li>{@code POST /api/auth/login}: HTTP Basic against the user accounts (phone number + password), no other
 * authentication accepted.</li>
 * <li>Everything else: bearer JWT access tokens (resource server) with the role matrix
 * {@code permitAll} for auth, OpenAPI and the H2 console; {@code USER|ADMIN} for every {@code GET /api/**},
 * {@code POST /api/reservations} and {@code GET /api/users/me}; {@code ADMIN} for the rest of {@code /api/**}.</li>
 * </ol>
 * Failures are RFC 7807 problems: 401 from {@link RestAuthenticationEntryPoint}, 403 from
 * {@link RestAccessDeniedHandler}.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    static final String LOGIN_PATH = "/api/auth/login";
    static final String[] PUBLIC_PATHS = {"/api/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"};
    static final String H2_CONSOLE_PATH = "/h2-console/**";
    static final String ROLE_USER = RoleDto.USER.name();
    static final String ROLE_ADMIN = RoleDto.ADMIN.name();

    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /** The H2 console is public (and framed) only when it is enabled, i.e. in the {@code dev} profile. */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    /** Basic authentication against {@link UserDetailsService} with BCrypt; used only by the login chain. */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain loginFilterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        return http
                .securityMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, LOGIN_PATH))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .authenticationManager(authenticationManager)
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                              AccessTokenAuthenticationConverter authenticationConverter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // the H2 console renders itself in frames; everywhere else the default DENY stays
                .headers(headers -> headers.frameOptions(
                        frame -> {
                            if (h2ConsoleEnabled) {
                                frame.sameOrigin();
                            }
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(H2_CONSOLE_PATH)
                        .access((authentication, context) -> new AuthorizationDecision(h2ConsoleEnabled))
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(ROLE_USER, ROLE_ADMIN)
                        .requestMatchers(HttpMethod.HEAD, "/api/**").hasAnyRole(ROLE_USER, ROLE_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasAnyRole(ROLE_USER, ROLE_ADMIN)
                        .requestMatchers("/api/**").hasRole(ROLE_ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
