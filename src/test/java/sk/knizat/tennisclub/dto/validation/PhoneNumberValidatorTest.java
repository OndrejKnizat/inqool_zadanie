package sk.knizat.tennisclub.dto.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberValidatorTest {

    private record Holder(@NotBlank @PhoneNumber String phone) {
    }

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"+421900000001", "+421 900 000 001", "0900-123-456", "1234567"})
    void should_beValid_when_normalisedValueMatchesStrictPattern(String phone) {
        assertThat(validator.validate(new Holder(phone))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-------", "123456", "+4219000000012345", "+421 900 x"})
    void should_reportPhoneNumberViolation_when_valueInvalid(String phone) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(phone));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("phone");
        assertThat(violations.iterator().next().getMessage()).contains("phone number");
    }

    @Test
    void should_leaveNullToOtherConstraints_when_valueNull() {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(null));

        assertThat(violations).extracting(v -> v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
                .containsExactly("NotBlank");
    }
}
