package com.blindtest.service.port;

import java.time.Duration;

/** Port for transactional authentication emails. */
public interface EmailSender {

    /** Sends an account-verification message. */
    void sendVerificationEmail(String toEmail, String username, String verificationToken);

    /** Sends a password-reset message. */
    void sendPasswordResetEmail(String toEmail, String username, String resetToken);

    /** Sends an account invitation whose registration link carries {@code invitationToken}. */
    void sendInvitationEmail(
            String toEmail, String inviterUsername, String invitationToken, Duration validity);

    /** Sends the welcome message once an account is ready to use. */
    void sendWelcomeEmail(String toEmail, String username);
}
