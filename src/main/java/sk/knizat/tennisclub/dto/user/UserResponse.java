package sk.knizat.tennisclub.dto.user;

import sk.knizat.tennisclub.dto.RoleDto;

import java.time.Instant;

/**
 * User as exposed by the API. The password hash is never exposed; {@code canLogin} tells whether the account
 * has a password (customers created implicitly by a reservation have none).
 *
 * @param id          identifier
 * @param phoneNumber normalised phone number (login name)
 * @param name        display name
 * @param role        role
 * @param canLogin    {@code true} when the account has a password and can authenticate
 * @param createdAt   creation time
 * @param updatedAt   last modification time
 */
public record UserResponse(
        Long id,
        String phoneNumber,
        String name,
        RoleDto role,
        boolean canLogin,
        Instant createdAt,
        Instant updatedAt) {
}
