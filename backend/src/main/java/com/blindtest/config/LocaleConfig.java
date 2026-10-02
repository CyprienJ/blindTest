package com.blindtest.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Resolves the request language from {@code Accept-Language}: French by default, or English. */
@Configuration
public class LocaleConfig {

    public static final Locale DEFAULT_LOCALE = Locale.FRENCH;

    /** Picks the best supported match for the header, falling back to French. */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.FRENCH, Locale.ENGLISH));
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        return resolver;
    }
}
