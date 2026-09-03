package sk.knizat.tennisclub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import sk.knizat.tennisclub.service.UserService;

/** Loads accounts for HTTP Basic login through {@link UserService}; the username is the phone number. */
@Component
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userService.findAuthUserByPhoneNumber(username)
                .map(AppUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User " + username + " not found"));
    }
}
