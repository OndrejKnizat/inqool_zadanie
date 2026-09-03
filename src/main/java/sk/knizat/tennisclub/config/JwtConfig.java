package sk.knizat.tennisclub.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import sk.knizat.tennisclub.security.JwtTokenService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Nimbus JWT encoder/decoder over the shared HS256 secret ({@code app.security.jwt.secret}).
 * <p>
 * The decoder is shared by the resource server (bearer access tokens) and {@link JwtTokenService} (refresh
 * tokens). Expiry is validated with a {@link JwtTimestampValidator} driven by the application {@link Clock}
 * and no clock skew, because the same application issues and validates the tokens; this also lets tests move
 * a controllable clock past the validity to observe expiry.
 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AppProperties properties;
    private final Clock clock;

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator, new JwtIssuerValidator(JwtTokenService.ISSUER)));
        return decoder;
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(properties.security().jwt().secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }
}
