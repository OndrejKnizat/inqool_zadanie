package sk.knizat.tennisclub.dto.surfacetype;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload for creating or fully updating a surface type.
 *
 * @param name           unique surface name; surrounding whitespace is trimmed
 * @param pricePerMinute non-negative price per minute, at most 2 decimal places
 */
public record SurfaceTypeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal pricePerMinute) {
}
