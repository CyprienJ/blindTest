package com.blindtest.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Produces an internal duplicate-detection key for manually configured aliases.
 *
 * <p>This is deliberately not the future gameplay matching contract.
 */
@Component
public class AnswerAliasNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Applies Unicode compatibility normalization, case folding, and whitespace collapse. */
    public String normalize(String value) {
        String unicode = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return WHITESPACE.matcher(unicode.strip().toLowerCase(Locale.ROOT)).
                replaceAll(" ");
    }
}
