package sk.knizat.tennisclub.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import sk.knizat.tennisclub.dao.UserDao;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.UserResponse;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.User;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.mapper.UserMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String PHONE = "+421900000001";

    @Mock
    private UserDao userDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userDao, new UserMapper(), passwordEncoder,
                Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC));
    }

    private static User user(Long id, String hash, Role role) {
        User user = new User();
        user.setId(id);
        user.setPhoneNumber(PHONE);
        user.setName("Jane");
        user.setPasswordHash(hash);
        user.setRole(role);
        return user;
    }

    @Test
    void should_returnAuthUserForNormalisedPhone_when_userExists() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(1L, "hash", Role.ADMIN)));

        Optional<AuthUser> result = service.findAuthUserByPhoneNumber("+421 900-000 001");

        assertThat(result).contains(new AuthUser(1L, PHONE, "hash", RoleDto.ADMIN));
    }

    @Test
    void should_returnEmpty_when_authUserMissing() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        assertThat(service.findAuthUserByPhoneNumber(PHONE)).isEmpty();
    }

    @Test
    void should_returnEmptyWithoutQuery_when_loginNameIsNotAPhoneNumber() {
        assertThat(service.findAuthUserByPhoneNumber("admin")).isEmpty();
        assertThat(service.findAuthUserByPhoneNumber(null)).isEmpty();
        verifyNoInteractions(userDao);
    }

    @Test
    void should_returnResponse_when_findByPhoneNumberExists() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(1L, null, Role.USER)));

        UserResponse response = service.findByPhoneNumber("+421 900 000 001");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.canLogin()).isFalse();
        assertThat(response.role()).isEqualTo(RoleDto.USER);
    }

    @Test
    void should_throwNotFound_when_findByPhoneNumberMissing() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByPhoneNumber(PHONE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User with phone number " + PHONE + " not found");
    }

    @Test
    void should_throwValidation_when_findByPhoneNumberMalformed() {
        assertThatThrownBy(() -> service.findByPhoneNumber("abc")).isInstanceOf(ValidationException.class);
    }

    @Test
    void should_hashPasswordAndSave_when_createAccount() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("$2a$hashed");
        when(userDao.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        UserResponse response = service.createAccount("+421 900 000 001", "  Admin  ", "secret", RoleDto.ADMIN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(saved.getName()).isEqualTo("Admin");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$hashed");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.canLogin()).isTrue();
        assertThat(response.role()).isEqualTo(RoleDto.ADMIN);
    }

    @Test
    void should_throwConflict_when_createAccountPhoneTaken() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(1L, null, Role.USER)));

        assertThatThrownBy(() -> service.createAccount(PHONE, "Jane", "secret", RoleDto.USER))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User with phone number " + PHONE + " already exists");
        verify(userDao, never()).save(any());
    }

    @Test
    void should_throwConflict_when_createAccountRacesOnUniqueIndex() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hash");
        when(userDao.save(any())).thenThrow(new DataIntegrityViolationException("unique index"));

        assertThatThrownBy(() -> service.createAccount(PHONE, "Jane", "secret", RoleDto.USER))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User with phone number " + PHONE + " already exists");
    }

    @Test
    void should_throwValidation_when_createAccountPasswordBlank() {
        assertThatThrownBy(() -> service.createAccount(PHONE, "Jane", " ", RoleDto.USER))
                .isInstanceOf(ValidationException.class)
                .hasMessage("password must not be blank");
        assertThatThrownBy(() -> service.createAccount(PHONE, "Jane", null, RoleDto.USER))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userDao, passwordEncoder);
    }

    @Test
    void should_throwValidation_when_createAccountPhoneMalformed() {
        assertThatThrownBy(() -> service.createAccount("12", "Jane", "secret", RoleDto.USER))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(userDao);
    }
}
