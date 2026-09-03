package sk.knizat.tennisclub.dto.reservation;

import sk.knizat.tennisclub.dto.GameTypeDto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reservation as exposed by the API. {@code price} is the snapshot computed when the reservation was created
 * or last updated (scale 2).
 */
public record ReservationResponse(
        Long id,
        Integer courtNumber,
        String courtName,
        Instant startTime,
        Instant endTime,
        GameTypeDto gameType,
        BigDecimal price,
        CustomerResponse customer,
        Instant createdAt) {
}
