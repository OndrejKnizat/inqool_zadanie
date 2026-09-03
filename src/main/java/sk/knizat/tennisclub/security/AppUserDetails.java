package sk.knizat.tennisclub.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security adapter over {@link AuthUser}. Named {@code AppUserDetails} because the entity {@code User}
 * clashes with Spring Security's own {@code User}. The username is the phone number, the single authority is
 * {@code ROLE_<role>}, and an account without a password is disabled (it cannot log in).
 */
public final class AppUserDetails implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    private final AuthUser user;

    public AppUserDetails(AuthUser user) {
        this.user = user;
    }

    public Long getId() {
        return user.id();
    }

    public RoleDto getRole() {
        return user.role();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + user.role().name()));
    }

    @Override
    public String getPassword() {
        return user.passwordHash();
    }

    @Override
    public String getUsername() {
        return user.phoneNumber();
    }

    @Override
    public boolean isEnabled() {
        return user.canLogin();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
