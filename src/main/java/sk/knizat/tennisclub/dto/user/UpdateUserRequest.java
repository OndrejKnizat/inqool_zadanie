package sk.knizat.tennisclub.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sk.knizat.tennisclub.dto.RoleDto;

/**
 * Payload for updating a user account by an ADMIN. The phone number (login name) cannot be changed.
 *
 * @param name     display name (trimmed)
 * @param role     new role
 * @param password optional new plain password, 8 to 72 characters (BCrypt input limit); {@code null} keeps
 *                 the current password (or the absence of one)
 */
public record UpdateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull RoleDto role,
        @Size(min = 8, max = 72) String password) {
}
