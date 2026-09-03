package sk.knizat.tennisclub.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import sk.knizat.tennisclub.config.AppProperties;
import sk.knizat.tennisclub.dao.CourtDao;
import sk.knizat.tennisclub.dao.ReservationDao;
import sk.knizat.tennisclub.dao.UserDao;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.reservation.UpdateReservationRequest;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.GameType;
import sk.knizat.tennisclub.entity.Reservation;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.SurfaceType;
import sk.knizat.tennisclub.entity.User;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.mapper.ReservationMapper;
import sk.knizat.tennisclub.service.PriceCalculator;
import sk.knizat.tennisclub.support.Fixtures;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");
    private static final Instant START = NOW.plus(Duration.ofDays(1));
    private static final Instant END = START.plus(Duration.ofMinutes(90));
    private static final String PHONE = "+421900000001";

    @Mock
    private ReservationDao reservationDao;

    @Mock
    private CourtDao courtDao;

    @Mock
    private UserDao userDao;

    private ReservationServiceImpl service;

    private Court court;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.DataInit(false),
                new AppProperties.Security(
                        new AppProperties.Security.Jwt("0123456789012345678901234567890123", Duration.ofMinutes(15),
                                Duration.ofDays(7)),
                        new AppProperties.Security.Admin(false, "+420000000000", "Admin", "admin")),
                new AppProperties.Reservation(Duration.ofMinutes(15), Duration.ofHours(4)));
        service = new ReservationServiceImpl(reservationDao, courtDao, userDao, new ReservationMapper(),
                new PriceCalculator(), properties, Clock.fixed(NOW, ZoneOffset.UTC));

        SurfaceType clay = Fixtures.surfaceType("Clay", "2.00");
        clay.setId(3L);
        court = Fixtures.court(1, clay);
        court.setId(10L);
    }

    private static CreateReservationRequest createRequest(Instant start, Instant end, GameTypeDto type) {
        return new CreateReservationRequest(1, start, end, type, PHONE, "Jane");
    }

    private static CreateReservationRequest createRequest(Instant start, Instant end) {
        return createRequest(start, end, GameTypeDto.SINGLES);
    }

    private static User user(Long id, String phone, String name) {
        User user = Fixtures.user(phone, name);
        user.setId(id);
        return user;
    }

    private Reservation existing(Long id, Instant start, Instant end) {
        Reservation reservation = Fixtures.reservation(court, user(7L, PHONE, "Stored"), start, end);
        reservation.setId(id);
        return reservation;
    }

    private void stubCourtLocked() {
        when(courtDao.findByCourtNumber(1)).thenReturn(Optional.of(court));
        when(courtDao.findByIdForUpdate(10L)).thenReturn(Optional.of(court));
    }

    private void stubSaveReturnsArgument() {
        when(reservationDao.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(100L);
            }
            return entity;
        });
    }

    // ---- create: validation --------------------------------------------------------------------------------

    @Test
    void should_throwValidation_when_startNotBeforeEnd() {
        assertThatThrownBy(() -> service.create(createRequest(START, START)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("startTime must be before endTime");
        assertThatThrownBy(() -> service.create(createRequest(END, START)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("startTime must be before endTime");
        verifyNoInteractions(courtDao, reservationDao, userDao);
    }

    @Test
    void should_throwValidation_when_startInPast() {
        Instant past = NOW.minus(Duration.ofMinutes(1));

        assertThatThrownBy(() -> service.create(createRequest(past, past.plus(Duration.ofHours(1)))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("startTime must not be in the past");
        verifyNoInteractions(courtDao);
    }

    @Test
    void should_acceptStart_when_startEqualsNow() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(7L, PHONE, "Stored")));
        stubSaveReturnsArgument();

        ReservationResponse result = service.create(createRequest(NOW, NOW.plus(Duration.ofMinutes(15))));

        assertThat(result.startTime()).isEqualTo(NOW);
    }

    @Test
    void should_throwValidation_when_tooShort() {
        assertThatThrownBy(() -> service.create(createRequest(START, START.plus(Duration.ofMinutes(14)))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Reservation must last at least 15 minutes");
    }

    @Test
    void should_throwValidation_when_tooLong() {
        assertThatThrownBy(() -> service.create(createRequest(START, START.plus(Duration.ofMinutes(241)))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Reservation must last at most 240 minutes");
    }

    @Test
    void should_throwValidation_when_notWholeMinute() {
        Instant withSeconds = START.plusSeconds(30);
        Instant withNanos = START.plusNanos(1);

        assertThatThrownBy(() -> service.create(createRequest(withSeconds, END)))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("startTime and endTime must be whole minutes");
        assertThatThrownBy(() -> service.create(createRequest(START, END.plusSeconds(1))))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("startTime and endTime must be whole minutes");
        assertThatThrownBy(() -> service.create(createRequest(withNanos, END)))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("startTime and endTime must be whole minutes");
    }

    @Test
    void should_throwNotFound_when_courtNumberUnknown() {
        when(courtDao.findByCourtNumber(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest(START, END)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Court with number 1 not found");
        verify(courtDao, never()).findByIdForUpdate(any());
        verifyNoInteractions(reservationDao, userDao);
    }

    @Test
    void should_throwNotFound_when_courtDeletedBeforeLock() {
        when(courtDao.findByCourtNumber(1)).thenReturn(Optional.of(court));
        when(courtDao.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest(START, END)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Court with number 1 not found");
        verifyNoInteractions(reservationDao);
    }

    @Test
    void should_throwConflict_when_courtLockTimesOut() {
        when(courtDao.findByCourtNumber(1)).thenReturn(Optional.of(court));
        when(courtDao.findByIdForUpdate(10L)).thenThrow(new CannotAcquireLockException("Timeout trying to lock"));

        assertThatThrownBy(() -> service.create(createRequest(START, END)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Court with number 1 is locked by a concurrent reservation, please retry");
        verifyNoInteractions(reservationDao, userDao);
    }

    @Test
    void should_throwValidationAndLockBeforeOverlapCheck_when_overlapExists() {
        stubCourtLocked();
        when(reservationDao.existsOverlapping(10L, START, END, null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(START, END)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Reservation overlaps with an existing reservation on court 1");

        InOrder order = inOrder(courtDao, reservationDao);
        order.verify(courtDao).findByIdForUpdate(10L);
        order.verify(reservationDao).existsOverlapping(10L, START, END, null);
        verify(reservationDao, never()).save(any());
        verifyNoInteractions(userDao);
    }

    @Test
    void should_throwValidation_when_phoneInvalidAfterNormalisation() {
        stubCourtLocked();
        CreateReservationRequest request =
                new CreateReservationRequest(1, START, END, GameTypeDto.SINGLES, "12 34", "Jane");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("is not a valid phone number");
        verifyNoInteractions(userDao);
        verify(reservationDao, never()).save(any());
    }

    // ---- create: happy paths -------------------------------------------------------------------------------

    @Test
    void should_reuseExistingUserWithStoredName_when_phoneKnown() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(7L, PHONE, "Stored")));
        stubSaveReturnsArgument();

        ReservationResponse result = service.create(createRequest(START, END));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.customer().phoneNumber()).isEqualTo(PHONE);
        assertThat(result.customer().name()).isEqualTo("Stored");
        verify(userDao, never()).save(any());
    }

    @Test
    void should_createUserWithRoleUserAndNoPassword_when_phoneUnknown() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(userDao.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(8L);
            return user;
        });
        stubSaveReturnsArgument();
        CreateReservationRequest request =
                new CreateReservationRequest(1, START, END, GameTypeDto.SINGLES, "+421 900-000 001", "  Jane ");

        ReservationResponse result = service.create(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getPhoneNumber()).isEqualTo(PHONE);
        assertThat(created.getName()).isEqualTo("Jane");
        assertThat(created.getRole()).isEqualTo(Role.USER);
        assertThat(created.getPasswordHash()).isNull();
        assertThat(result.customer().name()).isEqualTo("Jane");
        assertThat(result.customer().phoneNumber()).isEqualTo(PHONE);
    }

    @Test
    void should_snapshotSinglesPrice_when_created() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(7L, PHONE, "Stored")));
        stubSaveReturnsArgument();

        ReservationResponse result = service.create(createRequest(START, END, GameTypeDto.SINGLES));

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationDao).save(captor.capture());
        Reservation saved = captor.getValue();
        assertThat(saved.getCourt()).isSameAs(court);
        assertThat(saved.getStartTime()).isEqualTo(START);
        assertThat(saved.getEndTime()).isEqualTo(END);
        assertThat(saved.getGameType()).isEqualTo(GameType.SINGLES);
        assertThat(saved.getPrice()).isEqualTo(new BigDecimal("180.00"));
        assertThat(result.price()).isEqualTo(new BigDecimal("180.00"));
        assertThat(result.gameType()).isEqualTo(GameTypeDto.SINGLES);
        assertThat(result.courtNumber()).isEqualTo(1);
        assertThat(result.courtName()).isEqualTo("Court 1");
    }

    @Test
    void should_snapshotDoublesPrice_when_created() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(7L, PHONE, "Stored")));
        stubSaveReturnsArgument();

        ReservationResponse result = service.create(createRequest(START, END, GameTypeDto.DOUBLES));

        assertThat(result.price()).isEqualTo(new BigDecimal("270.00"));
        assertThat(result.gameType()).isEqualTo(GameTypeDto.DOUBLES);
    }

    // ---- findById / delete ---------------------------------------------------------------------------------

    @Test
    void should_returnResponse_when_findByIdExists() {
        when(reservationDao.findById(5L)).thenReturn(Optional.of(existing(5L, START, END)));

        ReservationResponse result = service.findById(5L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.customer().name()).isEqualTo("Stored");
    }

    @Test
    void should_throwNotFound_when_findByIdMissing() {
        when(reservationDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Reservation with id 9 not found");
    }

    @Test
    void should_softDeleteWithClockTime_when_deleteExists() {
        Reservation reservation = existing(5L, START, END);
        when(reservationDao.findById(5L)).thenReturn(Optional.of(reservation));

        service.delete(5L);

        verify(reservationDao).softDelete(reservation, NOW);
    }

    @Test
    void should_throwNotFound_when_deleteMissing() {
        when(reservationDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L)).isInstanceOf(NotFoundException.class);
        verify(reservationDao, never()).softDelete(any(), any());
    }

    // ---- update --------------------------------------------------------------------------------------------

    @Test
    void should_throwNotFound_when_updateMissing() {
        when(reservationDao.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new UpdateReservationRequest(1, START, END, GameTypeDto.SINGLES)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Reservation with id 9 not found");
        verifyNoInteractions(courtDao);
    }

    @Test
    void should_throwValidation_when_updateMovesIntoPast() {
        when(reservationDao.findById(5L)).thenReturn(Optional.of(existing(5L, START, END)));
        Instant past = NOW.minus(Duration.ofHours(1));

        assertThatThrownBy(() -> service.update(5L,
                new UpdateReservationRequest(1, past, past.plus(Duration.ofHours(1)), GameTypeDto.SINGLES)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("startTime must not be in the past");
        verifyNoInteractions(courtDao);
    }

    @Test
    void should_excludeOwnIdFromOverlapCheck_when_updating() {
        when(reservationDao.findById(5L)).thenReturn(Optional.of(existing(5L, START, END)));
        stubCourtLocked();
        when(reservationDao.existsOverlapping(10L, START, END, 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(5L, new UpdateReservationRequest(1, START, END, GameTypeDto.SINGLES)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Reservation overlaps with an existing reservation on court 1");

        InOrder order = inOrder(courtDao, reservationDao);
        order.verify(courtDao).findByIdForUpdate(10L);
        order.verify(reservationDao).existsOverlapping(10L, START, END, 5L);
        verify(reservationDao, never()).save(any());
    }

    @Test
    void should_recalculatePriceAndKeepCustomer_when_updateValid() {
        Reservation reservation = existing(5L, START, END);
        User originalCustomer = reservation.getUser();
        when(reservationDao.findById(5L)).thenReturn(Optional.of(reservation));
        SurfaceType grass = Fixtures.surfaceType("Grass", "3.00");
        Court other = Fixtures.court(2, grass);
        other.setId(20L);
        when(courtDao.findByCourtNumber(2)).thenReturn(Optional.of(other));
        when(courtDao.findByIdForUpdate(20L)).thenReturn(Optional.of(other));
        when(reservationDao.existsOverlapping(20L, START, START.plus(Duration.ofMinutes(60)), 5L)).thenReturn(false);
        stubSaveReturnsArgument();

        ReservationResponse result = service.update(5L,
                new UpdateReservationRequest(2, START, START.plus(Duration.ofMinutes(60)), GameTypeDto.DOUBLES));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.courtNumber()).isEqualTo(2);
        assertThat(result.endTime()).isEqualTo(START.plus(Duration.ofMinutes(60)));
        assertThat(result.gameType()).isEqualTo(GameTypeDto.DOUBLES);
        assertThat(result.price()).isEqualTo(new BigDecimal("270.00"));
        assertThat(reservation.getUser()).isSameAs(originalCustomer);
        assertThat(result.customer().name()).isEqualTo("Stored");
        verifyNoInteractions(userDao);
    }

    @Test
    void should_throwNotFound_when_updateTargetsUnknownCourt() {
        when(reservationDao.findById(5L)).thenReturn(Optional.of(existing(5L, START, END)));
        when(courtDao.findByCourtNumber(3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new UpdateReservationRequest(3, START, END, GameTypeDto.SINGLES)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Court with number 3 not found");
    }

    // ---- findAll -------------------------------------------------------------------------------------------

    @Test
    void should_listAllOrderedByStartTime_when_noFilter() {
        when(reservationDao.findAllOrderByStartTime()).thenReturn(List.of(existing(1L, START, END)));

        List<ReservationResponse> result = service.findAll(null, null, false);

        assertThat(result).extracting(ReservationResponse::id).containsExactly(1L);
        verify(reservationDao, never()).findByCourtNumberOrderByCreatedAt(any());
        verify(reservationDao, never()).findByPhoneNumber(anyString(), anyBoolean(), any());
    }

    @Test
    void should_filterFutureInMemory_when_noFilterAndFutureOnly() {
        Reservation past = existing(1L, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));
        Reservation ongoing = existing(2L, NOW, NOW.plus(Duration.ofHours(1)));
        Reservation future = existing(3L, NOW.plus(Duration.ofMinutes(1)), NOW.plus(Duration.ofHours(1)));
        when(reservationDao.findAllOrderByStartTime()).thenReturn(List.of(past, ongoing, future));

        List<ReservationResponse> result = service.findAll(null, null, true);

        assertThat(result).extracting(ReservationResponse::id).containsExactly(3L);
    }

    @Test
    void should_useCourtQuery_when_courtNumberOnly() {
        when(reservationDao.findByCourtNumberOrderByCreatedAt(1)).thenReturn(List.of(existing(2L, START, END)));

        List<ReservationResponse> result = service.findAll(1, null, false);

        assertThat(result).extracting(ReservationResponse::id).containsExactly(2L);
    }

    @Test
    void should_returnEmptyList_when_courtNumberUnknown() {
        when(reservationDao.findByCourtNumberOrderByCreatedAt(99)).thenReturn(List.of());

        assertThat(service.findAll(99, null, false)).isEmpty();
    }

    @Test
    void should_filterFutureInMemory_when_courtNumberAndFutureOnly() {
        Reservation past = existing(1L, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));
        Reservation future = existing(3L, START, END);
        when(reservationDao.findByCourtNumberOrderByCreatedAt(1)).thenReturn(List.of(past, future));

        List<ReservationResponse> result = service.findAll(1, null, true);

        assertThat(result).extracting(ReservationResponse::id).containsExactly(3L);
    }

    @Test
    void should_normalisePhoneAndForwardFutureOnly_when_phoneNumberOnly() {
        Reservation past = existing(1L, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)));
        when(reservationDao.findByPhoneNumber(PHONE, true, NOW)).thenReturn(List.of(past));

        List<ReservationResponse> result = service.findAll(null, "+421 900 000 001", true);

        // the DAO applied futureOnly in JPQL; the service does not filter the phone list again
        assertThat(result).extracting(ReservationResponse::id).containsExactly(1L);
        verify(reservationDao, never()).findAllOrderByStartTime();
    }

    @Test
    void should_intersectInMemoryKeepingPhoneOrder_when_bothFilters() {
        Court other = Fixtures.court(2, court.getSurfaceType());
        other.setId(20L);
        Reservation onOther = Fixtures.reservation(other, user(7L, PHONE, "Stored"), START, END);
        onOther.setId(1L);
        Reservation later = existing(2L, START.plus(Duration.ofHours(5)), END.plus(Duration.ofHours(5)));
        Reservation earlier = existing(3L, START, END);
        when(reservationDao.findByPhoneNumber(PHONE, false, NOW)).thenReturn(List.of(onOther, earlier, later));

        List<ReservationResponse> result = service.findAll(1, PHONE, false);

        assertThat(result).extracting(ReservationResponse::id).containsExactly(3L, 2L);
        verify(reservationDao, never()).findByCourtNumberOrderByCreatedAt(any());
    }

    @Test
    void should_throwValidation_when_phoneFilterMalformed() {
        assertThatThrownBy(() -> service.findAll(null, "abc", false))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(reservationDao);
    }

    @Test
    void should_throwConflict_when_customerCreatedConcurrentlyWithSamePhone() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.empty());
        when(userDao.save(any(User.class))).thenThrow(new DataIntegrityViolationException("ux_app_user_phone_active"));

        assertThatThrownBy(() -> service.create(createRequest(START, END)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(PHONE);
        verify(reservationDao, never()).save(any());
    }

    @Test
    void should_accept_when_durationEqualsMin() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(7L, PHONE, "Stored")));
        stubSaveReturnsArgument();

        ReservationResponse result = service.create(createRequest(START, START.plus(Duration.ofMinutes(15))));

        assertThat(result.endTime()).isEqualTo(START.plus(Duration.ofMinutes(15)));
    }

    @Test
    void should_accept_when_durationEqualsMax() {
        stubCourtLocked();
        when(userDao.findByPhoneNumber(PHONE)).thenReturn(Optional.of(user(7L, PHONE, "Stored")));
        stubSaveReturnsArgument();

        ReservationResponse result = service.create(createRequest(START, START.plus(Duration.ofMinutes(240))));

        assertThat(result.endTime()).isEqualTo(START.plus(Duration.ofMinutes(240)));
    }
}
