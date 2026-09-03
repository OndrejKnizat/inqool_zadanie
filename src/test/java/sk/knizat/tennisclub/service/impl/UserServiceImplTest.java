package sk.knizat.tennisclub.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import sk.knizat.tennisclub.dao.ReservationDao;
import sk.knizat.tennisclub.dao.UserDao;
import sk.knizat.tennisclub.dto.RoleDto;
import sk.knizat.tennisclub.dto.user.AuthUser;
import sk.knizat.tennisclub.dto.user.CreateUserRequest;
import sk.knizat.tennisclub.dto.user.UpdateUserRequest;
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
import java.util.List;
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
    private static final String OTHER_PHONE = "+421900000002";
    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock
    private UserDao userDao;

    @Mock
    private ReservationDao reservationDao;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userDao, reservationDao, new UserMapper(), passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
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

    // --- findAuthUserByPhoneNumber / findByPhoneNumber ---

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

    // --- createAccount ---

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

    // --- findAll / findById ---

    @Test
    void should_mapAllUsersWithoutHash_when_findAll() {
        when(userDao.findAll()).thenReturn(List.of(user(1L, "hash", Role.ADMIN), user(2L, null, Role.USER)));

        List<UserResponse> result = service.findAll();

        assertThat(result).extracting(UserResponse::id).containsExactly(1L, 2L);
        assertThat(result).extracting(UserResponse::canLogin).containsExactly(true, false);
        assertThat(result).extracting(UserResponse::role).containsExactly(RoleDto.ADMIN, RoleDto.USER);
    }

    @Test
    void should_returnEmptyList_when_findAllHasNoUsers() {
        when(userDao.findAll()).thenReturn(List.of());

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void should_returnResponse_when_findByIdExists() {
        when(userDao.findById(1L)).thenReturn(Optional.of(user(1L, "hash", Role.USER)));

        UserResponse response = service.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.phoneNumber()).isEqualTo(PHONE);
        assertThat(response.name()).isEqualTo("Jane");
        assertThat(response.canLogin()).isTrue();
    }

    @Test
    void should_throwNotFound_when_findByIdMissing() {
        when(userDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User with id 9 not found");
    }

    // --- create ---

    @Test
    void should_hashPasswordAndNormalisePhone_when_createFromRequest() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("jane-secret")).thenReturn("$2a$jane");
        when(userDao.save(any())).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        UserResponse response = service.create(
                new CreateUserRequest("+421 900-000 001", " Jane ", "jane-secret", RoleDto.USER));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(captor.capture());
        assertThat(captor.getValue().getPhoneNumber()).isEqualTo(PHONE);
        assertThat(captor.getValue().getName()).isEqualTo("Jane");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$jane");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.phoneNumber()).isEqualTo(PHONE);
        assertThat(response.canLogin()).isTrue();
    }

    @Test
    void should_throwConflict_when_createFromRequestPhoneTaken() {
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(1L, null, Role.USER)));

        assertThatThrownBy(() -> service.create(new CreateUserRequest(PHONE, "Jane", "jane-secret", RoleDto.USER)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User with phone number " + PHONE + " already exists");
        verify(userDao, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    // --- update ---

    @Test
    void should_updateNameAndRoleAndKeepPassword_when_updateWithoutPassword() {
        User existing = user(1L, "$2a$old", Role.USER);
        when(userDao.findById(1L)).thenReturn(Optional.of(existing));
        when(userDao.save(existing)).thenReturn(existing);

        UserResponse response = service.update(1L, new UpdateUserRequest("  Jane Doe ", RoleDto.ADMIN, null));

        assertThat(existing.getName()).isEqualTo("Jane Doe");
        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existing.getPasswordHash()).isEqualTo("$2a$old");
        assertThat(existing.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(response.name()).isEqualTo("Jane Doe");
        assertThat(response.role()).isEqualTo(RoleDto.ADMIN);
        assertThat(response.canLogin()).isTrue();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void should_rehashPassword_when_updateWithPassword() {
        User existing = user(1L, null, Role.USER);
        when(userDao.findById(1L)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("new-secret")).thenReturn("$2a$new");
        when(userDao.save(existing)).thenReturn(existing);

        UserResponse response = service.update(1L, new UpdateUserRequest("Jane", RoleDto.USER, "new-secret"));

        assertThat(existing.getPasswordHash()).isEqualTo("$2a$new");
        assertThat(response.canLogin()).isTrue();
        verify(passwordEncoder).encode("new-secret");
    }

    @Test
    void should_throwValidation_when_updatePasswordBlank() {
        when(userDao.findById(1L)).thenReturn(Optional.of(user(1L, "$2a$old", Role.USER)));

        assertThatThrownBy(() -> service.update(1L, new UpdateUserRequest("Jane", RoleDto.USER, "        ")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("password must not be blank");
        verify(userDao, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void should_throwNotFound_when_updateMissing() {
        when(userDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new UpdateUserRequest("Jane", RoleDto.USER, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User with id 9 not found");
        verify(userDao, never()).save(any());
    }

    // --- delete ---

    @Test
    void should_softDeleteWithClockInstant_when_deleteAllowed() {
        User existing = user(1L, "hash", Role.USER);
        when(userDao.findById(1L)).thenReturn(Optional.of(existing));
        when(reservationDao.existsUnfinishedByUser(1L, NOW)).thenReturn(false);

        service.delete(1L, OTHER_PHONE);

        verify(userDao).softDelete(existing, NOW);
    }

    @Test
    void should_throwConflict_when_deleteSelf() {
        when(userDao.findById(1L)).thenReturn(Optional.of(user(1L, "hash", Role.ADMIN)));

        assertThatThrownBy(() -> service.delete(1L, "+421 900 000 001"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User with id 1 is the current user and cannot delete itself");
        verify(userDao, never()).softDelete(any(), any());
        verifyNoInteractions(reservationDao);
    }

    @Test
    void should_throwConflict_when_deleteUserWithUnfinishedReservations() {
        when(userDao.findById(1L)).thenReturn(Optional.of(user(1L, null, Role.USER)));
        when(reservationDao.existsUnfinishedByUser(1L, NOW)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L, OTHER_PHONE))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User with id 1 has unfinished reservations and cannot be deleted");
        verify(userDao, never()).softDelete(any(), any());
    }

    @Test
    void should_throwNotFound_when_deleteMissing() {
        when(userDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L, OTHER_PHONE))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User with id 9 not found");
        verifyNoInteractions(reservationDao);
    }

    @Test
    void should_throwConflict_when_demotingLastAdmin() {
        when(userDao.findById(1L)).thenReturn(Optional.of(user(1L, "hash", Role.ADMIN)));
        when(userDao.countActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.update(1L, new UpdateUserRequest("Admin", RoleDto.USER, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User with id 1 is the last administrator");
        verify(userDao, never()).save(any());
    }

    @Test
    void should_demoteAdmin_when_anotherAdminExists() {
        User existing = user(1L, "hash", Role.ADMIN);
        when(userDao.findById(1L)).thenReturn(Optional.of(existing));
        when(userDao.countActiveAdmins()).thenReturn(2L);
        when(userDao.save(existing)).thenReturn(existing);

        UserResponse response = service.update(1L, new UpdateUserRequest("Admin", RoleDto.USER, null));

        assertThat(response.role()).isEqualTo(RoleDto.USER);
    }

    @Test
    void should_throwConflict_when_deletingLastAdmin() {
        when(userDao.findById(2L)).thenReturn(Optional.of(user(2L, "hash", Role.ADMIN)));
        when(userDao.countActiveAdmins()).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(2L, OTHER_PHONE))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User with id 2 is the last administrator");
        verify(userDao, never()).softDelete(any(), any());
    }

    @Test
    void should_softDeleteAdmin_when_anotherAdminExists() {
        User existing = user(2L, "hash", Role.ADMIN);
        when(userDao.findById(2L)).thenReturn(Optional.of(existing));
        when(userDao.countActiveAdmins()).thenReturn(2L);
        when(reservationDao.existsUnfinishedByUser(2L, NOW)).thenReturn(false);

        service.delete(2L, OTHER_PHONE);

        verify(userDao).softDelete(existing, NOW);
    }
}
