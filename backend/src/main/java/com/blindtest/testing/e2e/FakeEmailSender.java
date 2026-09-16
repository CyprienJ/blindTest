package com.blindtest.testing.e2e;

import com.blindtest.service.port.EmailSender;
import java.util.ArrayList;
import java.util.List;

/**
 * Captures transactional emails in memory instead of calling Resend. Active only under the
 * {@code e2e} Spring profile, and never eligible to run in production; see
 * {@link E2eAdaptersConfig}.
 */
public class FakeEmailSender implements EmailSender {

    private final List<CapturedEmail> messages = new ArrayList<>();

    @Override
    public synchronized void sendVerificationEmail(
            String toEmail,
            String username,
            String verificationToken) {
        messages.add(new CapturedEmail(EmailType.VERIFICATION, toEmail, username, verificationToken));
    }

    @Override
    public synchronized void sendPasswordResetEmail(
            String toEmail,
            String username,
            String resetToken) {
        messages.add(new CapturedEmail(EmailType.PASSWORD_RESET, toEmail, username, resetToken));
    }

    @Override
    public synchronized void sendWelcomeEmail(String toEmail, String username) {
        messages.add(new CapturedEmail(EmailType.WELCOME, toEmail, username, null));
    }

    /** Returns a stable copy of every captured message, most recent last. */
    public synchronized List<CapturedEmail> messages() {
        return List.copyOf(messages);
    }

    /** Discards every captured message so full-stack specs start from a clean inbox. */
    public synchronized void reset() {
        messages.clear();
    }

    /** Transactional email categories captured by this fake. */
    public enum EmailType {
        VERIFICATION,
        PASSWORD_RESET,
        WELCOME
    }

    /** A captured email with the signed token a real message would have linked to. */
    public record CapturedEmail(
            EmailType type,
            String toEmail,
            String username,
            String token) {
    }
}
