package com.blindtest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

class EmailTemplatesTest {

    private static final String BASE_URL = "https://nosblindtests.fr";

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/emails");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    void rendersEveryEmailInFrench() {
        EmailTemplates french = new EmailTemplates(messages(), BASE_URL, Locale.FRENCH);

        EmailTemplates.RenderedEmail invitation =
                french.invitation("Léa", BASE_URL + "/auth/register?invite=abc", 72);
        assertThat(invitation.subject()).isEqualTo("Léa vous invite sur Blindtest");
        assertThat(invitation.html()).
                startsWith("<!DOCTYPE html><html lang=\"fr\">").
                contains("Choisissez un nom d'utilisateur").
                contains("ne fonctionne qu'une fois, pendant 72 heures.").
                contains("Créer mon compte →").
                contains(BASE_URL + "/auth/register?invite=abc");

        assertThat(french.verification("Léa", "https://x/verify").subject()).
                isEqualTo("Confirmez votre email pour commencer à jouer");
        assertThat(french.passwordReset("Léa", "https://x/reset").html()).
                contains("Réinitialisez votre mot de passe, Léa");
        assertThat(french.welcome("Léa").html()).
                contains("Bienvenue sur Blindtest, Léa !").
                contains(BASE_URL + "/lobby/create");
    }

    @Test
    void rendersEveryEmailInEnglish() {
        EmailTemplates english = new EmailTemplates(messages(), BASE_URL, Locale.ENGLISH);

        assertThat(english.invitation("Max", "https://x/invite", 72).subject()).
                isEqualTo("Max invited you to Blindtest");
        assertThat(english.verification("Max", "https://x/verify").html()).
                startsWith("<!DOCTYPE html><html lang=\"en\">").
                contains("Welcome, Max — one quick step").
                contains("Didn't sign up?");
        assertThat(english.passwordReset("Max", "https://x/reset").subject()).
                isEqualTo("Reset your Blindtest password");
        assertThat(english.welcome("Max").subject()).isEqualTo("You're in — let's start a blindtest");
    }

    @Test
    void escapesUserControlledText() {
        EmailTemplates french = new EmailTemplates(messages(), BASE_URL, Locale.FRENCH);

        String html = french.invitation("<b>Mallory</b>", "https://x/invite", 72).html();

        assertThat(html).contains("&lt;b&gt;Mallory&lt;/b&gt; vous invite").doesNotContain("<b>Mallory");
    }
}
