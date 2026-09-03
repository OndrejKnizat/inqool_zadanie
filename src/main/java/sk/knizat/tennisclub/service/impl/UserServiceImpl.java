package sk.knizat.tennisclub.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.knizat.tennisclub.dao.UserDao;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.dto.validation.PhoneNumbers;
import sk.knizat.tennisclub.entity.User;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.mapper.UserMapper;
import sk.knizat.tennisclub.service.UserService;

import java.time.Clock;
import java.util.Optional;

/** Default {@link UserService} backed by {@link UserDao}; passwords are hashed with the {@link PasswordEncoder}. */
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserDao userDao;
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    /** Used by the update/delete operations added in step 8 (soft delete timestamp). */
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
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ValidationException("password must not be blank");
        }
        if (userDao.findByPhoneNumber(normalised).isPresent()) {
            throw duplicatePhone(normalised);
        }
        User user = new User();
        user.setPhoneNumber(normalised);
        user.setName(name.trim());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(mapper.toEntity(role));
        return mapper.toResponse(UniqueKeys.saveOrConflict(() -> userDao.save(user),
                duplicatePhone(normalised).getMessage()));
    }

    private static ConflictException duplicatePhone(String phoneNumber) {
        return new ConflictException("User with phone number " + phoneNumber + " already exists");
    }
}
