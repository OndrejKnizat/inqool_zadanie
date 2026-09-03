package sk.knizat.tennisclub.dto.reservation;

/**
 * Customer of a reservation as embedded in {@link ReservationResponse}.
 *
 * @param phoneNumber normalised phone number (no spaces or hyphens)
 * @param name        stored customer name
 */
public record CustomerResponse(String phoneNumber, String name) {
}
