package sk.knizat.tennisclub.dto.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import sk.knizat.tennisclub.dto.GameTypeDto;

import java.time.Instant;

/**
 * Payload for fully updating a reservation. The customer cannot be changed (delete and create instead).
 *
 * @param courtNumber business number of an existing court
 * @param startTime   start of the half-open interval, whole minutes
 * @param endTime     end of the half-open interval, whole minutes
 * @param gameType    singles or doubles
 */
public record UpdateReservationRequest(
        @NotNull @Positive Integer courtNumber,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull GameTypeDto gameType) {
}
