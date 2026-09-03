package sk.knizat.tennisclub.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean Validation constraint for phone numbers: after removing spaces and hyphens the value must match
 * {@value PhoneNumbers#STRICT_PATTERN}. {@code null} is valid (combine with {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = PhoneNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    String message() default "must be a phone number: optional + followed by 7 to 15 digits (spaces and hyphens are ignored)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
