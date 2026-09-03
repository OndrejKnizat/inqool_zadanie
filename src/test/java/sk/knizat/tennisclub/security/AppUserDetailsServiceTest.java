package sk.knizat.tennisclub.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.service.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AppUserDetailsService service;

    @Test
    void should_returnAdapter_when_userExists() {
        when(userService.findAuthUserByPhoneNumber("+421 900 000 001"))
                .thenReturn(Optional.of(new AuthUser(1L, "+421900000001", "hash", RoleDto.USER)));

        UserDetails details = service.loadUserByUsername("+421 900 000 001");

        assertThat(details).isInstanceOf(AppUserDetails.class);
        assertThat(details.getUsername()).isEqualTo("+421900000001");
        assertThat(details.getPassword()).isEqualTo("hash");
    }

    @Test
    void should_throwUsernameNotFound_when_userMissing() {
        when(userService.findAuthUserByPhoneNumber("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User nobody not found");
    }
}
