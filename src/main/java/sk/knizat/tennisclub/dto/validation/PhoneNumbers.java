package sk.knizat.tennisclub.dto.validation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import sk.knizat.tennisclub.exception.ValidationException;

import java.util.regex.Pattern;

/**
 * Phone number normalisation (ARCHITECTURE.md O-14).
 * <p>
 * Validation: DTO fields annotated with {@link PhoneNumber} are validated through {@link #normalise(String)},
 * which removes all whitespace and hyphens and checks
 * the strict form {@value #STRICT_PATTERN}. The normalised value is what gets stored, compared and used as
 * login, so {@code "+421 900 000 001"} and {@code "+421900000001"} are the same customer, while
 * {@code "+421..."} and {@code "421..."} are two different ones (no prefix completion).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhoneNumbers {

    /** Strict form of a normalised phone number: optional plus followed by 7 to 15 digits. */
    public static final String STRICT_PATTERN = "^\\+?[0-9]{7,15}$";

    private static final Pattern STRICT = Pattern.compile(STRICT_PATTERN);
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-]+");

    /**
     * Strips whitespace and hyphens and validates the result.
     *
     * @param raw phone number as received from the client
     * @return normalised phone number
     * @throws ValidationException when the normalised value does not match {@value #STRICT_PATTERN}
     */
    public static String normalise(String raw) {
        if (raw == null) {
            throw new ValidationException("phoneNumber must not be null");
        }
        String normalised = SEPARATORS.matcher(raw).replaceAll("");
        if (!STRICT.matcher(normalised).matches()) {
            throw new ValidationException(
                    "phoneNumber '" + raw + "' is not a valid phone number (expected " + STRICT_PATTERN
                            + " after removing spaces and hyphens)");
        }
        return normalised;
    }
}
