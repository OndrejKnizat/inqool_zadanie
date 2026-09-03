package sk.knizat.tennisclub.service;

import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.CreateUserRequest;
import sk.knizat.tennisclub.dto.user.UpdateUserRequest;
import sk.knizat.tennisclub.dto.user.UserResponse;

import java.util.List;
import java.util.Optional;

/**
 * User accounts (customers and system users are the same entity, ARCHITECTURE.md O-1). Phone numbers are
 * normalised (spaces and hyphens removed) before every lookup or store.
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

    /** All non-deleted users ordered by id. */
    List<UserResponse> findAll();

    /**
     * Finds a non-deleted user by id.
     *
     * @throws sk.knizat.tennisclub.exception.NotFoundException when no such user exists
     */
    UserResponse findById(Long id);

    /**
     * Creates an account from the API payload; see {@link #createAccount(String, String, String, RoleDto)}.
     */
    UserResponse create(CreateUserRequest request);

    /**
     * Updates name and role of a user and, when a password is given, replaces the password hash.
     *
     * @throws sk.knizat.tennisclub.exception.NotFoundException when no such user exists
     * @throws sk.knizat.tennisclub.exception.ValidationException when the given password is blank
     */
    UserResponse update(Long id, UpdateUserRequest request);

    /**
     * Soft-deletes a user. Past reservations stay as history.
     *
     * @param id               user to delete
     * @param currentUserPhone phone number (principal name) of the caller, who cannot delete themselves
     * @throws sk.knizat.tennisclub.exception.NotFoundException when no such user exists
     * @throws sk.knizat.tennisclub.exception.ConflictException when the user is the caller or has unfinished
     *                                                          reservations (ARCHITECTURE.md O-9)
     */
    void delete(Long id, String currentUserPhone);
}
