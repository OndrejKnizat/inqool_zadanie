package sk.knizat.tennisclub.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the bootstrap admin switch turned on: the context starts with
 * {@code app.security.admin.enabled=true}, so {@link AdminInitializer} must have created the account. The
 * properties fork a dedicated context with its own in-memory database, so no cleanup is needed.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.admin.enabled=true",
        "app.security.admin.phone-number=+421 111 222 333",
        "app.security.admin.name=Boot Admin",
        "app.security.admin.password=boot-secret"})
class AdminInitializerEnabledTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void should_registerInitializerBean_when_adminEnabled() {
        assertThat(context.getBeanNamesForType(AdminInitializer.class)).hasSize(1);
    }

    @Test
    void should_createAdminThatCanLogIn_when_adminEnabled() {
        UserResponse admin = userService.findByPhoneNumber("+421111222333");
        assertThat(admin.name()).isEqualTo("Boot Admin");
        assertThat(admin.role()).isEqualTo(RoleDto.ADMIN);
        assertThat(admin.canLogin()).isTrue();

        AuthUser authUser = userService.findAuthUserByPhoneNumber("+421111222333").orElseThrow();
        assertThat(passwordEncoder.matches("boot-secret", authUser.passwordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong", authUser.passwordHash())).isFalse();
    }
}
