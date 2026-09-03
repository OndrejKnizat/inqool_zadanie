package sk.knizat.tennisclub.service;

import sk.knizat.tennisclub.dto.reservation.CreateReservationRequest;
import sk.knizat.tennisclub.dto.reservation.ReservationResponse;
import sk.knizat.tennisclub.dto.reservation.UpdateReservationRequest;

import java.util.List;

/** Use cases of reservations: creation with customer find-or-create, read, update, soft delete. */
public interface ReservationService {

    /**
     * Lists non-deleted reservations. All filters are optional and combinable:
     * <ul>
     *   <li>{@code courtNumber} only: reservations of that court ordered by creation time; an unknown
     *       court number yields an empty list (it is a filter, not a lookup);</li>
     *   <li>{@code phoneNumber} only: reservations of that (normalised) phone ordered by start time;</li>
     *   <li>both: the phone list restricted to the court (start time order);</li>
     *   <li>none: all reservations ordered by start time.</li>
     * </ul>
     * {@code futureOnly} keeps only reservations starting strictly after now, whatever the other filters.
     *
     * @throws sk.knizat.tennisclub.exception.ValidationException when the phone number is malformed
     */
    List<ReservationResponse> findAll(Integer courtNumber, String phoneNumber, boolean futureOnly);

    /** @throws sk.knizat.tennisclub.exception.NotFoundException when missing or deleted */
    ReservationResponse findById(Long id);

    /**
     * Creates a reservation: validates the interval, locks the court, rejects overlaps, finds the customer
     * by phone number or creates a new one, and snapshots the price.
     *
     * @throws sk.knizat.tennisclub.exception.ValidationException invalid interval, malformed phone, overlap
     * @throws sk.knizat.tennisclub.exception.NotFoundException   unknown or deleted court number
     */
    ReservationResponse create(CreateReservationRequest request);

    /**
     * Moves the reservation (court, interval, game type) and recalculates the price. The customer is kept.
     * The new interval is validated like on creation, so it must not start in the past: a reservation that
     * has already started can no longer be changed.
     *
     * @throws sk.knizat.tennisclub.exception.NotFoundException   reservation or court not found
     * @throws sk.knizat.tennisclub.exception.ValidationException invalid interval or overlap
     */
    ReservationResponse update(Long id, UpdateReservationRequest request);

    /** @throws sk.knizat.tennisclub.exception.NotFoundException when missing or deleted */
    void delete(Long id);
}
