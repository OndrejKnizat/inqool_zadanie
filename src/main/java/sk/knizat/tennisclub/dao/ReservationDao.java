package sk.knizat.tennisclub.dao;

import sk.knizat.tennisclub.entity.Reservation;

import java.time.Instant;
import java.util.List;

/** DAO of {@link Reservation}; list queries eagerly fetch court, surface type and user. */
public interface ReservationDao extends GenericDao<Reservation> {

    /**
     * Non-deleted reservations of the court with the given number, oldest first. The court itself may be
     * soft-deleted: history of a removed court stays readable by its number.
     */
    List<Reservation> findByCourtNumberOrderByCreatedAt(Integer courtNumber);

    /**
     * Non-deleted reservations of the non-deleted user with the given phone number, ordered by start time.
     *
     * @param futureOnly when {@code true} only reservations starting strictly after {@code now} are returned
     * @param now        reference time (ignored when {@code futureOnly} is {@code false})
     */
    List<Reservation> findByPhoneNumber(String phoneNumber, boolean futureOnly, Instant now);

    /** All non-deleted reservations ordered by start time. */
    List<Reservation> findAllOrderByStartTime();

    /**
     * Tells whether a non-deleted reservation on the court overlaps the half-open interval
     * {@code [start, end)}: {@code r.startTime < end AND r.endTime > start}. Touching intervals do not overlap.
     *
     * @param excludeReservationId reservation to ignore (the one being updated), may be {@code null}
     */
    boolean existsOverlapping(Long courtId, Instant start, Instant end, Long excludeReservationId);

    /**
     * Tells whether the court has a non-deleted reservation that has not finished yet ({@code endTime > now}).
     * Used to block deletion: an ongoing reservation blocks as well as a future one.
     */
    boolean existsUnfinishedByCourt(Long courtId, Instant now);

    /** Tells whether the user has a non-deleted reservation that has not finished yet ({@code endTime > now}). */
    boolean existsUnfinishedByUser(Long userId, Instant now);
}
