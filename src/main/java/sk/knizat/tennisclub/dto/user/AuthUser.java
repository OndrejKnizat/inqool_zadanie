package sk.knizat.tennisclub.dto.user;

import sk.knizat.tennisclub.dto.RoleDto;

/**
 * Internal view of a user for authentication only: it carries the password hash so the security layer can
 * verify credentials without touching the entity. It must never be returned by a controller.
 *
 * @param id           identifier
 * @param phoneNumber  normalised phone number (login name)
 * @param passwordHash BCrypt hash, {@code null} when the account cannot log in
 * @param role         role
 */
public record AuthUser(Long id, String phoneNumber, String passwordHash, RoleDto role) {

    /** Tells whether the account has a password and can therefore authenticate. */
    public boolean canLogin() {
        return passwordHash != null;
    }
}
