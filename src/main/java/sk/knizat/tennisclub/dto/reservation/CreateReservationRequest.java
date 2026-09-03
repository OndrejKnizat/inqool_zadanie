package sk.knizat.tennisclub.dto.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sk.knizat.tennisclub.dto.GameTypeDto;
import sk.knizat.tennisclub.dto.validation.PhoneNumber;

import java.time.Instant;

/**
 * Payload for creating a reservation.
 * <p>
 * The phone number is validated by {@link PhoneNumber}: spaces and hyphens are removed, the rest must match
 * {@code ^\+?[0-9]{7,15}$}; the service stores the normalised form. Interval rules (whole minutes, not in
 * the past, allowed duration, no overlap) are business validation in the service.
 *
 * @param courtNumber  business number of an existing court
 * @param startTime    start of the half-open interval, whole minutes
 * @param endTime      end of the half-open interval, whole minutes
 * @param gameType     singles or doubles (doubles cost 1.5x)
 * @param phoneNumber  customer phone number; identifies an existing customer or creates a new one
 * @param customerName customer name; used only when a new customer is created
 */
public record CreateReservationRequest(
        @NotNull @Positive Integer courtNumber,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull GameTypeDto gameType,
        @NotBlank @PhoneNumber String phoneNumber,
        @NotBlank @Size(max = 100) String customerName) {
}
