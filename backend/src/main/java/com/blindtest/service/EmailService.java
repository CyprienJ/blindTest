package com.blindtest.service;

import com.blindtest.config.LocaleConfig;
import com.blindtest.config.properties.ApplicationProperties;
import com.blindtest.config.properties.ResendProperties;
import com.blindtest.service.port.EmailSender;
import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails via the Resend API, in the language of the current request
 * ({@code Accept-Language}, French by default).
 */
@Service
public class EmailService implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private static final String FROM_HELLO = "Blindtest <hello@nosblindtests.fr>";

    private final Resend resend;
    private final String appBaseUrl;
    private final MessageSource messages;

    /** Constructs the service with a Resend API key, the application base URL, and email texts. */
    public EmailService(
            ResendProperties resendProperties,
            ApplicationProperties applicationProperties,
            MessageSource messages) {
        this.resend = new Resend(resendProperties.apiKey());
        this.appBaseUrl = applicationProperties.baseUrl().toString();
        this.messages = messages;
    }

    /**
     * Sends an email-verification message to {@code toEmail}.
     * The link embeds a signed JWT that expires in 24 hours.
     */
    @Override
    public void sendVerificationEmail(String toEmail, String username, String verificationToken) {
        send(toEmail, templates().verification(
                username, appBaseUrl + "/verify-email?token=" + verificationToken));
    }

    /**
     * Sends a password-reset email with a signed JWT link (TTL 1 hour).
     */
    @Override
    public void sendPasswordResetEmail(String toEmail, String username, String resetToken) {
        send(toEmail, templates().passwordReset(
                username, appBaseUrl + "/reset-password?token=" + resetToken));
    }

    /**
     * Sends an account invitation. The link embeds a one-time token valid for {@code validity}.
     */
    @Override
    public void sendInvitationEmail(
            String toEmail, String inviterUsername, String invitationToken, Duration validity) {
        send(toEmail, templates().invitation(
                inviterUsername,
                appBaseUrl + "/auth/register?invite=" + invitationToken,
                validity.toHours()));
    }

    /**
     * Sends a welcome email after the user has verified their address.
     */
    @Override
    public void sendWelcomeEmail(String toEmail, String username) {
        send(toEmail, templates().welcome(username));
    }

    /**
     * Emails are sent while handling the request that triggered them, so its locale applies;
     * outside a request (for example the administrator CLI) they are sent in French.
     */
    private EmailTemplates templates() {
        LocaleContext context = LocaleContextHolder.getLocaleContext();
        Locale locale = context == null || context.getLocale() == null
                ? LocaleConfig.DEFAULT_LOCALE
                : context.getLocale();
        return new EmailTemplates(messages, appBaseUrl, locale);
    }

    private void send(String to, EmailTemplates.RenderedEmail email) {
        CreateEmailOptions options = CreateEmailOptions.builder().
                from(FROM_HELLO).
                to(to).
                subject(email.subject()).
                html(email.html()).
                build();
        try {
            resend.emails().send(options);
        } catch (Exception e) {
            LOG.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
