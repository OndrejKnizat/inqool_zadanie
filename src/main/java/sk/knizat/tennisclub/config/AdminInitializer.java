package sk.knizat.tennisclub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.service.UserService;

import java.util.Optional;

/**
 * Bootstraps the first ADMIN account from {@code app.security.admin.*} (ARCHITECTURE.md O-3) when
 * {@code app.security.admin.enabled=true}; independent of {@code app.data-init}. The account is created only
 * when no non-deleted user with the configured phone number exists, so restarts are safe.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.security.admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AppProperties properties;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Security.Admin admin = properties.security().admin();
        Optional<AuthUser> existing = userService.findAuthUserByPhoneNumber(admin.phoneNumber());
        if (existing.isPresent()) {
            AuthUser account = existing.get();
            if (account.role() != RoleDto.ADMIN || !account.canLogin()) {
                log.warn("Bootstrap admin account {} already exists but is role {} and canLogin={}; "
                        + "it is left untouched, fix it through the user management API",
                        admin.phoneNumber(), account.role(), account.canLogin());
            } else {
                log.info("Bootstrap admin account {} already exists", admin.phoneNumber());
            }
            return;
        }
        userService.createAccount(admin.phoneNumber(), admin.name(), admin.password(), RoleDto.ADMIN);
        log.info("Bootstrap admin account {} ({}) created", admin.phoneNumber(), admin.name());
    }
}
