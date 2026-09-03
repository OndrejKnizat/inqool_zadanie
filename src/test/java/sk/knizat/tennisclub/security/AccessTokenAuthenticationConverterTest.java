package sk.knizat.tennisclub.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenAuthenticationConverterTest {

    private final AccessTokenAuthenticationConverter converter = new AccessTokenAuthenticationConverter();

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject("+421900000001");
    }

    @Test
    void should_mapRoleClaimToAuthority_when_accessToken() {
        AbstractAuthenticationToken authentication =
                converter.convert(jwt().claim("type", "access").claim("role", "USER").build());

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("+421900000001");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void should_reject_when_refreshToken() {
        Jwt refresh = jwt().claim("type", "refresh").claim("role", "USER").build();

        assertThatThrownBy(() -> converter.convert(refresh))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("not an access token");
    }

    @Test
    void should_reject_when_typeClaimMissing() {
        Jwt untyped = jwt().claim("role", "USER").build();

        assertThatThrownBy(() -> converter.convert(untyped)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void should_reject_when_roleClaimMissing() {
        Jwt noRole = jwt().claim("type", "access").build();

        assertThatThrownBy(() -> converter.convert(noRole))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("no role claim");
    }
}
