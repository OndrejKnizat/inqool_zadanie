package sk.knizat.tennisclub.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.knizat.tennisclub.config.AppProperties;
import sk.knizat.tennisclub.dao.CourtDao;
import sk.knizat.tennisclub.dao.ReservationDao;
import sk.knizat.tennisclub.dao.UserDao;
import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.reservation.UpdateReservationRequest;
import sk.knizat.tennisclub.entity.Court;
import sk.knizat.tennisclub.entity.GameType;
import sk.knizat.tennisclub.entity.Reservation;
import sk.knizat.tennisclub.entity.Role;
import sk.knizat.tennisclub.entity.User;
import sk.knizat.tennisclub.exception.ConflictException;
import sk.knizat.tennisclub.exception.NotFoundException;
import sk.knizat.tennisclub.exception.ValidationException;
import sk.knizat.tennisclub.mapper.ReservationMapper;
import sk.knizat.tennisclub.dto.validation.PhoneNumbers;
import sk.knizat.tennisclub.service.PriceCalculator;
import sk.knizat.tennisclub.service.ReservationService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Default {@link ReservationService}.
 * <p>
 * Creation and update run inside one transaction that acquires a {@code PESSIMISTIC_WRITE} lock on the
 * target court row <em>before</em> the overlap check, so two concurrent requests for the same court are
 * serialised: the second one waits for the first to commit and then sees its reservation (O-7). A lock that
 * cannot be acquired within the database lock timeout is translated by Spring into a
 * {@link PessimisticLockingFailureException}, which is turned into a {@link ConflictException} (409) here.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private static final String ENTITY = "Reservation";
    private static final long SECONDS_PER_MINUTE = 60L;

    private final ReservationDao reservationDao;
    private final CourtDao courtDao;
    private final UserDao userDao;
    private final ReservationMapper mapper;
    private final PriceCalculator priceCalculator;
    private final AppProperties properties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> findAll(Integer courtNumber, String phoneNumber, boolean futureOnly) {
        Instant now = clock.instant();
        List<Reservation> found;
        if (phoneNumber != null) {
            found = reservationDao.findByPhoneNumber(PhoneNumbers.normalise(phoneNumber), futureOnly, now);
            if (courtNumber != null) {
                // intersection in memory keeps the start-time order of the phone list
                found = found.stream().filter(r -> courtNumber.equals(r.getCourt().getCourtNumber())).toList();
            }
        } else if (courtNumber != null) {
            found = reservationDao.findByCourtNumberOrderByCreatedAt(courtNumber);
        } else {
            found = reservationDao.findAllOrderByStartTime();
        }
        if (futureOnly && phoneNumber == null) {
            // the phone query already applied the filter in JPQL; the other lists filter here (O-15)
            found = found.stream().filter(r -> r.getStartTime().isAfter(now)).toList();
        }
        return found.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id) {
        return mapper.toResponse(getOrThrow(id));
    }

    @Override
    public ReservationResponse create(CreateReservationRequest request) {
        validateInterval(request.startTime(), request.endTime());
        Court court = lockCourt(request.courtNumber());
        ensureNoOverlap(court, request.startTime(), request.endTime(), null);
        User customer = findOrCreateCustomer(request.phoneNumber(), request.customerName());

        Reservation reservation = new Reservation();
        reservation.setUser(customer);
        apply(reservation, court, request.startTime(), request.endTime(), mapper.toEntity(request.gameType()));
        return mapper.toResponse(reservationDao.save(reservation));
    }

    @Override
    public ReservationResponse update(Long id, UpdateReservationRequest request) {
        Reservation reservation = getOrThrow(id);
        validateInterval(request.startTime(), request.endTime());
        Court court = lockCourt(request.courtNumber());
        ensureNoOverlap(court, request.startTime(), request.endTime(), id);
        // customer stays as is (O-12)
        apply(reservation, court, request.startTime(), request.endTime(), mapper.toEntity(request.gameType()));
        return mapper.toResponse(reservationDao.save(reservation));
    }

    @Override
    public void delete(Long id) {
        reservationDao.softDelete(getOrThrow(id), clock.instant());
    }

    private Reservation getOrThrow(Long id) {
        return reservationDao.findById(id).orElseThrow(() -> NotFoundException.of(ENTITY, id));
    }

    /**
     * Interval rules (O-5): whole minutes, {@code start < end}, not in the past (applies to updates too: a
     * reservation cannot be moved into the past) and a duration within the configured bounds.
     */
    private void validateInterval(Instant start, Instant end) {
        if (!isWholeMinute(start) || !isWholeMinute(end)) {
            throw new ValidationException("startTime and endTime must be whole minutes (seconds must be zero)");
        }
        if (!start.isBefore(end)) {
            throw new ValidationException("startTime must be before endTime");
        }
        if (start.isBefore(clock.instant())) {
            throw new ValidationException("startTime must not be in the past");
        }
        Duration duration = Duration.between(start, end);
        Duration min = properties.reservation().minDuration();
        Duration max = properties.reservation().maxDuration();
        if (duration.compareTo(min) < 0) {
            throw new ValidationException("Reservation must last at least " + min.toMinutes() + " minutes");
        }
        if (duration.compareTo(max) > 0) {
            throw new ValidationException("Reservation must last at most " + max.toMinutes() + " minutes");
        }
    }

    private static boolean isWholeMinute(Instant instant) {
        return instant.getNano() == 0 && instant.getEpochSecond() % SECONDS_PER_MINUTE == 0;
    }

    /**
     * Resolves the court by its business number and takes the row lock. The court is addressed by number,
     * so the not-found message names the number rather than a database id. A lock timeout (another
     * transaction holds the court for too long) is a state conflict the client may retry, hence 409.
     * Spring reports it as {@link PessimisticLockingFailureException} or its subclass
     * {@code CannotAcquireLockException}; the catch covers both.
     */
    private Court lockCourt(Integer courtNumber) {
        Court court = courtDao.findByCourtNumber(courtNumber).orElseThrow(() -> notFoundCourt(courtNumber));
        try {
            return courtDao.findByIdForUpdate(court.getId()).orElseThrow(() -> notFoundCourt(courtNumber));
        } catch (PessimisticLockingFailureException ex) {
            throw new ConflictException(
                    "Court with number " + courtNumber + " is locked by a concurrent reservation, please retry");
        }
    }

    private static NotFoundException notFoundCourt(Integer courtNumber) {
        return new NotFoundException("Court with number " + courtNumber + " not found");
    }

    /** Overlap is a 400, not a 409 (O-10). Touching intervals are allowed by the DAO query. */
    private void ensureNoOverlap(Court court, Instant start, Instant end, Long excludeId) {
        if (reservationDao.existsOverlapping(court.getId(), start, end, excludeId)) {
            throw new ValidationException(
                    "Reservation overlaps with an existing reservation on court " + court.getCourtNumber());
        }
    }

    /**
     * Existing customers keep their stored name; the request name is used only for a new one (O-13).
     * The court lock does not cover customers, so two concurrent first bookings with the same new phone on
     * different courts are caught by the unique index on active phone numbers and answered with 409.
     */
    private User findOrCreateCustomer(String rawPhoneNumber, String name) {
        String phoneNumber = PhoneNumbers.normalise(rawPhoneNumber);
        return userDao.findByPhoneNumber(phoneNumber).orElseGet(() -> {
            User user = new User();
            user.setPhoneNumber(phoneNumber);
            user.setName(name.trim());
            user.setRole(Role.USER);
            user.setPasswordHash(null);
            return UniqueKeys.saveOrConflict(() -> userDao.save(user),
                    "Customer with phone number " + phoneNumber + " was created concurrently, please retry");
        });
    }

    private void apply(Reservation reservation, Court court, Instant start, Instant end, GameType gameType) {
        reservation.setCourt(court);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setGameType(gameType);
        reservation.setPrice(priceCalculator.calculate(start, end, court.getSurfaceType().getPricePerMinute(),
                gameType));
    }
}
