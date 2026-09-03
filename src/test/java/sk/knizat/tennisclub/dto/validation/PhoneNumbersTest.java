package sk.knizat.tennisclub.dto.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sk.knizat.tennisclub.exception.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumbersTest {

    @Test
    void should_removeSpaces_when_numberContainsSpaces() {
        assertThat(PhoneNumbers.normalise("+421 900 000 001")).isEqualTo("+421900000001");
        assertThat(PhoneNumbers.normalise("  +421900000001 ")).isEqualTo("+421900000001");
    }

    @Test
    void should_removeHyphens_when_numberContainsHyphens() {
        assertThat(PhoneNumbers.normalise("+421-900-000-001")).isEqualTo("+421900000001");
        assertThat(PhoneNumbers.normalise("0900 -- 123\t456")).isEqualTo("0900123456");
    }

    @Test
    void should_keepNumberWithoutPlus_when_plusIsMissing() {
        assertThat(PhoneNumbers.normalise("421900000001")).isEqualTo("421900000001");
    }

    @Test
    void should_acceptBoundaryLengths_when_sevenOrFifteenDigits() {
        assertThat(PhoneNumbers.normalise("1234567")).isEqualTo("1234567");
        assertThat(PhoneNumbers.normalise("+123456789012345")).isEqualTo("+123456789012345");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "+1234567890123456", "+421 900 00a 001", "++421900000001", "421+900000001", ""})
    void should_throwValidation_when_normalisedValueIsInvalid(String raw) {
        assertThatThrownBy(() -> PhoneNumbers.normalise(raw))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("is not a valid phone number");
    }

    @Test
    void should_throwValidation_when_null() {
        assertThatThrownBy(() -> PhoneNumbers.normalise(null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("phoneNumber must not be null");
    }
}
