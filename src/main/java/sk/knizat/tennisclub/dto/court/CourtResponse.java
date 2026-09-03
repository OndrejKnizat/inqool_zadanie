package sk.knizat.tennisclub.dto.court;

import sk.knizat.tennisclub.dto.surfacetype.SurfaceTypeResponse;

import java.time.Instant;

/** Court as exposed by the API, with its surface type embedded. */
public record CourtResponse(
        Long id,
        Integer courtNumber,
        String name,
        SurfaceTypeResponse surfaceType,
        Instant createdAt,
        Instant updatedAt) {
}
