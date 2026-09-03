package sk.knizat.tennisclub.dto.court;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or fully updating a court.
 *
 * @param courtNumber   positive business number, unique among non-deleted courts
 * @param name          optional display name; trimmed, blank becomes {@code null}
 * @param surfaceTypeId id of an existing, non-deleted surface type
 */
public record CourtRequest(
        @NotNull @Positive Integer courtNumber,
        @Size(max = 100) String name,
        @NotNull Long surfaceTypeId) {
}
