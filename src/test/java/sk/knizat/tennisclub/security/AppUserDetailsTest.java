package sk.knizat.tennisclub.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserDetailsTest {

    @Test
    void should_exposeAccountFields_when_userHasPassword() {
        AppUserDetails details = new AppUserDetails(new AuthUser(7L, "+421900000001", "$2a$hash", RoleDto.ADMIN));

        assertThat(details.getId()).isEqualTo(7L);
        assertThat(details.getRole()).isEqualTo(RoleDto.ADMIN);
        assertThat(details.getUsername()).isEqualTo("+421900000001");
        assertThat(details.getPassword()).isEqualTo("$2a$hash");
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void should_beDisabledWithUserRole_when_userHasNoPassword() {
        AppUserDetails details = new AppUserDetails(new AuthUser(8L, "+421900000002", null, RoleDto.USER));

        assertThat(details.isEnabled()).isFalse();
        assertThat(details.getPassword()).isNull();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }
}
