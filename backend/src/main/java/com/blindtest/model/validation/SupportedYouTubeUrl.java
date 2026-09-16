package com.blindtest.model.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates that a string is one of the supported YouTube video URL forms. */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SupportedYouTubeUrlValidator.class)
public @interface SupportedYouTubeUrl {

    /** Validation failure message. */
    String message() default "must be a supported YouTube video URL";

    /** Validation groups. */
    Class<?>[] groups() default {};

    /** Validation payload. */
    Class<? extends Payload>[] payload() default {};
}
