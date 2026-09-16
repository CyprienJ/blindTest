package com.blindtest.service.port;

/** Port for transactional authentication emails. */
public interface EmailSender {

    /** Sends an account-verification message. */
    void sendVerificationEmail(String toEmail, String username, String verificationToken);

    /** Sends a password-reset message. */
    void sendPasswordResetEmail(String toEmail, String username, String resetToken);

    /** Sends the post-verification welcome message. */
    void sendWelcomeEmail(String toEmail, String username);
}
