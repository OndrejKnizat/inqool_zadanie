package sk.knizat.tennisclub.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.knizat.tennisclub.dao.ReservationDao;
import sk.knizat.tennisclub.dao.UserDao;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.CreateUserRequest;
import sk.knizat.tennisclub.dto.user.UpdateUserRequest;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.dto.validation.PhoneNumbers;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.User;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.mapper.UserMapper;
import sk.knizat.tennisclub.service.UserService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Default {@link UserService} backed by {@link UserDao}; passwords are hashed with the {@link PasswordEncoder}. */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String ENTITY = "User";

    private final UserDao userDao;
    private final ReservationDao reservationDao;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<AuthUser> findAuthUserByPhoneNumber(String phoneNumber) {
        String normalised;
        try {
            normalised = PhoneNumbers.normalise(phoneNumber);
        } catch (ValidationException ex) {
            // a login name that is not a phone number simply matches no account
            return Optional.empty();
        }
        return userDao.findByPhoneNumber(normalised).map(mapper::toAuthUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findByPhoneNumber(String phoneNumber) {
        String normalised = PhoneNumbers.normalise(phoneNumber);
        return userDao.findByPhoneNumber(normalised)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("User with phone number " + normalised + " not found"));
    }

    @Override
    public UserResponse createAccount(String phoneNumber, String name, String rawPassword, RoleDto role) {
        String normalised = PhoneNumbers.normalise(phoneNumber);
        requireNotBlank(rawPassword);
        if (userDao.findByPhoneNumber(normalised).isPresent()) {
            throw duplicatePhone(normalised);
        }
        User user = new User();
        user.setPhoneNumber(normalised);
        user.setName(name.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(mapper.toEntity(role));
        UserResponse response = mapper.toResponse(UniqueKeys.saveOrConflict(() -> userDao.save(user),
                duplicatePhone(normalised).getMessage()));
        log.info("Created user {} with role {}", response.id(), role);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userDao.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Override
    public UserResponse create(CreateUserRequest request) {
        return createAccount(request.phoneNumber(), request.name(), request.password(), request.role());
    }

    @Override
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = getOrThrow(id);
        Role newRole = mapper.toEntity(request.role());
        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN) {
            requireAnotherAdmin(id);
        }
        user.setName(request.name().trim());
        user.setRole(newRole);
        if (request.password() != null) {
            requireNotBlank(request.password());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        UserResponse response = mapper.toResponse(userDao.save(user));
        log.info("Updated user {} (role {}, password changed: {})", id, newRole, request.password() != null);
        return response;
    }

    @Override
    public void delete(Long id, String currentUserPhone) {
        User user = getOrThrow(id);
        if (user.getPhoneNumber().equals(PhoneNumbers.normalise(currentUserPhone))) {
            throw new ConflictException("User with id " + id + " is the current user and cannot delete itself");
        }
        if (user.getRole() == Role.ADMIN) {
            requireAnotherAdmin(id);
        }
        Instant now = clock.instant();
        if (reservationDao.existsUnfinishedByUser(id, now)) {
            throw new ConflictException(
                    "User with id " + id + " has unfinished reservations and cannot be deleted");
        }
        userDao.softDelete(user, now);
        log.info("Deleted user {}", id);
    }

    /** The last administrator can be neither demoted nor deleted, otherwise nobody could manage the system. */
    private void requireAnotherAdmin(Long id) {
        if (userDao.countActiveAdmins() <= 1) {
            throw new ConflictException("User with id " + id + " is the last administrator");
        }
    }

    private User getOrThrow(Long id) {
        return userDao.findById(id).orElseThrow(() -> NotFoundException.of(ENTITY, id));
    }

    private static void requireNotBlank(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("password must not be blank");
        }
    }

    private static ConflictException duplicatePhone(String phoneNumber) {
        return new ConflictException("User with phone number " + phoneNumber + " already exists");
    }
}
