package com.blindtest.testing;

import com.blindtest.service.port.EmailSender;
import java.util.ArrayList;
import java.util.List;

/** In-memory transactional email adapter for integration tests. */
public class FakeEmailSender implements EmailSender {

    private final List<SentEmail> messages = new ArrayList<>();

    @Override
    public synchronized void sendVerificationEmail(
            String toEmail,
            String username,
            String verificationToken) {
        messages.add(new SentEmail(EmailType.VERIFICATION, toEmail, username, verificationToken));
    }

    @Override
    public synchronized void sendPasswordResetEmail(
            String toEmail,
            String username,
            String resetToken) {
        messages.add(new SentEmail(EmailType.PASSWORD_RESET, toEmail, username, resetToken));
    }

    @Override
    public synchronized void sendWelcomeEmail(String toEmail, String username) {
        messages.add(new SentEmail(EmailType.WELCOME, toEmail, username, null));
    }

    /** Returns a stable copy of all captured messages. */
    public synchronized List<SentEmail> messages() {
        return List.copyOf(messages);
    }

    /** Clears captured messages between tests. */
    public synchronized void reset() {
        messages.clear();
    }

    /** Authentication email categories captured by this fake. */
    public enum EmailType {
        VERIFICATION,
        PASSWORD_RESET,
        WELCOME
    }

    /** Captured email boundary values without provider-specific types. */
    public record SentEmail(
            EmailType type,
            String toEmail,
            String username,
            String token) {
    }
}
