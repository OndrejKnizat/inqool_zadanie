package sk.knizat.tennisclub.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.validation.PhoneNumber;

/**
 * Payload for creating a user account by an ADMIN.
 * <p>
 * The phone number is validated by {@link PhoneNumber} and stored normalised (spaces and hyphens removed);
 * it must be unique among non-deleted users. The password is stored as a BCrypt hash, hence the 72 character
 * limit (BCrypt input limit).
 *
 * @param phoneNumber login name
 * @param name        display name (trimmed)
 * @param password    plain password, 8 to 72 characters
 * @param role        role of the new account
 */
public record CreateUserRequest(
        @NotBlank @PhoneNumber String phoneNumber,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull RoleDto role) {
}
