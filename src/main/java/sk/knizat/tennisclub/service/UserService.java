package sk.knizat.tennisclub.service;

import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.UserResponse;

import java.util.Optional;

/**
 * User accounts (customers and system users are the same entity, ARCHITECTURE.md O-1). Phone numbers are
 * normalised (spaces and hyphens removed) before every lookup or store.
 * <p>
 * Account management endpoints ({@code /api/users}) are added in step 8 on top of these operations.
 */
public interface UserService {

    /**
     * Loads the authentication view of a non-deleted user for the security layer.
     *
     * @param phoneNumber raw phone number as typed by the client
     * @return the user, or empty when none exists or the phone number is not even a valid phone number
     */
    Optional<AuthUser> findAuthUserByPhoneNumber(String phoneNumber);

    /**
     * Finds a non-deleted user.
     *
     * @throws sk.knizat.tennisclub.exception.NotFoundException when no such user exists
     */
    UserResponse findByPhoneNumber(String phoneNumber);

    /**
     * Creates an account that can log in.
     *
     * @param phoneNumber raw phone number (normalised before storing)
     * @param name        display name
     * @param rawPassword plain password, stored as a BCrypt hash
     * @param role        role of the new account
     * @return the created user
     * @throws sk.knizat.tennisclub.exception.ConflictException when a non-deleted user with the phone number exists
     * @throws sk.knizat.tennisclub.exception.ValidationException when the phone number or password is invalid
     */
    UserResponse createAccount(String phoneNumber, String name, String rawPassword, RoleDto role);
}
