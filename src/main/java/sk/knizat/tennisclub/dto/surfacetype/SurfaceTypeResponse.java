package sk.knizat.tennisclub.dto.surfacetype;

import java.math.BigDecimal;
import java.time.Instant;

/** Surface type as exposed by the API. */
public record SurfaceTypeResponse(
        Long id,
        String name,
        BigDecimal pricePerMinute,
        Instant createdAt,
        Instant updatedAt) {
}
