package sk.knizat.tennisclub.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import sk.knizat.tennisclub.exception.ValidationException;

/** Validates {@link PhoneNumber} with the same normalisation the service applies before storing. */
public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            PhoneNumbers.normalise(value);
            return true;
        } catch (ValidationException ex) {
            return false;
        }
    }
}
