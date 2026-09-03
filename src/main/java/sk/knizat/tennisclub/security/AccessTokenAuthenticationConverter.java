package sk.knizat.tennisclub.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a decoded bearer JWT into an {@link org.springframework.security.core.Authentication}: the
 * {@code role} claim becomes {@code ROLE_<role>} and the principal name is the subject (phone number).
 * Tokens whose {@code type} is not {@value JwtTokenService#TYPE_ACCESS} (i.e. refresh tokens) are rejected
 * with an {@link InvalidBearerTokenException}, which the resource server reports as 401.
 */
@Component
public class AccessTokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (!JwtTokenService.TYPE_ACCESS.equals(jwt.getClaimAsString(JwtTokenService.CLAIM_TYPE))) {
            throw new InvalidBearerTokenException("Token is not an access token");
        }
        String role = jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE);
        if (role == null) {
            throw new InvalidBearerTokenException("Token has no role claim");
        }
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)), jwt.getSubject());
    }
}
