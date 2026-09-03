package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.service.UserService;
import sk.knizat.tennisclub.support.TestProperties;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserService userService;

    @Test
    void should_createAdminAccount_when_phoneNotTaken() {
        when(userService.findAuthUserByPhoneNumber("+420000000000")).thenReturn(Optional.empty());

        new AdminInitializer(TestProperties.defaults(), userService).run(new DefaultApplicationArguments());

        verify(userService).createAccount("+420000000000", "Administrator", "admin", RoleDto.ADMIN);
    }

    @Test
    void should_doNothing_when_accountAlreadyExists() {
        when(userService.findAuthUserByPhoneNumber("+420000000000"))
                .thenReturn(Optional.of(new AuthUser(1L, "+420000000000", "hash", RoleDto.ADMIN)));

        new AdminInitializer(TestProperties.defaults(), userService).run(new DefaultApplicationArguments());

        verify(userService, never()).createAccount(any(), any(), any(), any());
    }

    @Test
    void should_leaveAccountUntouched_when_existingAccountIsNotLoginCapableAdmin() {
        when(userService.findAuthUserByPhoneNumber("+420000000000"))
                .thenReturn(Optional.of(new AuthUser(1L, "+420000000000", null, RoleDto.USER)));

        new AdminInitializer(TestProperties.defaults(), userService).run(new DefaultApplicationArguments());

        verify(userService, never()).createAccount(any(), any(), any(), any());
    }
}
