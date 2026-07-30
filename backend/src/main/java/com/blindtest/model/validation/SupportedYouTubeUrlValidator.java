package com.blindtest.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Bean Validation adapter for {@link YouTubeVideoUrl}. */
public class SupportedYouTubeUrlValidator
        implements ConstraintValidator<SupportedYouTubeUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || YouTubeVideoUrl.parse(value).isPresent();
    }
}
