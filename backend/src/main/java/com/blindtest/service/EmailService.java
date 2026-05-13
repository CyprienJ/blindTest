package com.blindtest.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Sends transactional emails via the Resend API. */
@Service
public class EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private static final String FROM_HELLO = "Blindtest <hello@nosblindtests.fr>";

    private final Resend resend;
    private final String appBaseUrl;

    /** Constructs the service with a Resend API key and the application base URL. */
    public EmailService(
            @Value("${resend.api-key}") String apiKey,
            @Value("${app.base-url}") String appBaseUrl) {
        this.resend = new Resend(apiKey);
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Sends an email-verification message to {@code toEmail}.
     * The link embeds a signed JWT that expires in 24 hours.
     */
    public void sendVerificationEmail(String toEmail, String username, String verificationToken) {
        String link = appBaseUrl + "/verify-email?token=" + verificationToken;
        String html = buildVerificationEmailHtml(username, link);
        send(FROM_HELLO, toEmail, "Verify your email to start playing", html);
    }

    /**
     * Sends a password-reset email with a signed JWT link (TTL 1 hour).
     */
    public void sendPasswordResetEmail(String toEmail, String username, String resetToken) {
        String link = appBaseUrl + "/reset-password?token=" + resetToken;
        String html = buildPasswordResetEmailHtml(username, link);
        send(FROM_HELLO, toEmail, "Reset your Blindtest password", html);
    }

    /**
     * Sends a welcome email after the user has verified their address.
     */
    public void sendWelcomeEmail(String toEmail, String username) {
        String html = buildWelcomeEmailHtml(username);
        send(FROM_HELLO, toEmail, "You're in — let's start a blindtest", html);
    }

    private void send(String from, String to, String subject, String html) {
        var builder = CreateEmailOptions.builder().from(from).to(to);
        builder.subject(subject).html(html);
        CreateEmailOptions options = builder.build();
        try {
            resend.emails().send(options);
        } catch (ResendException e) {
            LOG.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    // ── HTML builders ──────────────────────────────────────────────────────────

    private String buildVerificationEmailHtml(String username, String link) {
        return "<!DOCTYPE html>"
            + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>Verify your email</title></head>"
            + "<body style=\"margin:0;padding:0;background:#f5f1ea;"
            + "font-family:'Manrope',-apple-system,system-ui,sans-serif;color:#1a1814\">"
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td align=\"center\" style=\"padding:40px 16px\">"
            + "<table width=\"100%\" style=\"max-width:560px;border-radius:14px;overflow:hidden;"
            + "border:1px solid #e9e3d8;box-shadow:0 18px 40px -22px rgba(0,0,0,.18)\">"
            // header
            + "<tr><td style=\"background:#22201c;padding:22px 28px\">"
            + "<table width=\"100%\"><tr>"
            + "<td><table><tr>"
            + "<td style=\"vertical-align:middle\">"
            + "<div style=\"width:26px;height:26px;background:#c9f062;border-radius:7px;"
            + "display:inline-block;vertical-align:middle;margin-right:10px\"></div>"
            + "<span style=\"color:#fff;font-weight:700;font-size:16px;"
            + "vertical-align:middle\">Blindtest</span></td></tr></table></td>"
            + "<td align=\"right\"><span style=\"font-size:10.5px;font-weight:700;"
            + "letter-spacing:.08em;padding:4px 8px;border-radius:4px;"
            + "background:#c9f062;color:#1f2a08;text-transform:uppercase\">VERIFY</span></td>"
            + "</tr></table></td></tr>"
            // body
            + "<tr><td style=\"background:#fff;padding:32px 28px 28px\">"
            + "<h1 style=\"font-size:24px;font-weight:700;letter-spacing:-.015em;"
            + "margin:0 0 14px;line-height:1.2\">Welcome, "
            + escapeHtml(username) + " — one quick step</h1>"
            + "<p style=\"font-size:14.5px;line-height:1.65;color:#1a1814;margin:0 0 16px\">"
            + "Confirm your email so you can host sessions and invite friends. "
            + "The link below works for 24 hours.</p>"
            + "<div style=\"margin:24px 0 12px\">"
            + "<a href=\"" + link + "\" style=\"display:inline-block;background:#c9f062;"
            + "color:#1f2a08;border:1.5px solid #c9f062;padding:13px 22px;border-radius:8px;"
            + "font-size:14px;font-weight:600;text-decoration:none\">Verify my email &rarr;</a>"
            + "</div>"
            + "<p style=\"font-size:14.5px;line-height:1.65;color:#4a4640;margin:0 0 16px\">"
            + "Or copy and paste this link into your browser:</p>"
            + "<div style=\"padding:12px 14px;background:#faf7f1;border:1px solid #e9e3d8;"
            + "border-radius:8px;font-family:'JetBrains Mono',ui-monospace,monospace;"
            + "font-size:11.5px;color:#4a4640;word-break:break-all\">"
            + escapeHtml(link) + "</div>"
            + "<div style=\"font-size:12px;color:#86807a;line-height:1.6;margin-top:18px;"
            + "padding-top:18px;border-top:1px dashed #e9e3d8\">"
            + "Didn't sign up? You can ignore this email — no account will be created.</div>"
            + "</td></tr>"
            // footer
            + "<tr><td style=\"background:#fff;padding:0 28px 28px\">"
            + "<div style=\"text-align:center;font-size:11.5px;color:#86807a;line-height:1.7\">"
            + "<div style=\"margin-bottom:8px\">"
            + "<a href=\"" + appBaseUrl + "/settings\" style=\"color:#4a4640;"
            + "text-decoration:none;margin-right:14px\">Settings</a>"
            + "<a href=\"" + appBaseUrl + "/unsubscribe\" style=\"color:#4a4640;"
            + "text-decoration:none;margin-right:14px\">Unsubscribe</a>"
            + "<a href=\"mailto:hello@nosblindtests.fr\" style=\"color:#4a4640;"
            + "text-decoration:none\">Help</a></div>"
            + "<div>Blindtest &middot; A real-time music game &middot; "
            + "You're getting this because you have an account.</div>"
            + "<div style=\"margin-top:4px\">&copy; 2026 Blindtest</div>"
            + "</div></td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    private String buildWelcomeEmailHtml(String username) {
        String sessionUrl = appBaseUrl + "/sessions/create";
        return "<!DOCTYPE html>"
            + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>Welcome to Blindtest</title></head>"
            + "<body style=\"margin:0;padding:0;background:#f5f1ea;"
            + "font-family:'Manrope',-apple-system,system-ui,sans-serif;color:#1a1814\">"
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td align=\"center\" style=\"padding:40px 16px\">"
            + "<table width=\"100%\" style=\"max-width:560px;border-radius:14px;overflow:hidden;"
            + "border:1px solid #e9e3d8;box-shadow:0 18px 40px -22px rgba(0,0,0,.18)\">"
            // header
            + "<tr><td style=\"background:#22201c;padding:22px 28px\">"
            + "<table><tr><td style=\"vertical-align:middle\">"
            + "<div style=\"width:26px;height:26px;background:#c9f062;border-radius:7px;"
            + "display:inline-block;vertical-align:middle;margin-right:10px\"></div>"
            + "<span style=\"color:#fff;font-weight:700;font-size:16px;"
            + "vertical-align:middle\">Blindtest</span>"
            + "</td></tr></table></td></tr>"
            // body
            + "<tr><td style=\"background:#fff;padding:32px 28px 28px\">"
            + "<h1 style=\"font-size:24px;font-weight:700;letter-spacing:-.015em;"
            + "margin:0 0 14px;line-height:1.2\">Welcome to Blindtest, "
            + escapeHtml(username) + "!</h1>"
            + "<p style=\"font-size:14.5px;line-height:1.65;color:#1a1814;margin:0 0 16px\">"
            + "Your email is verified. You can now host sessions, add friends, "
            + "and build a game history. Here's how players usually get rolling:</p>"
            + "<table width=\"100%\" style=\"border-collapse:collapse;margin:20px 0\">"
            + buildWelcomeStep("01", "Create your first session",
                "Pick a playlist or a decade and share the 6-character code.")
            + buildWelcomeStep("02", "Add a couple of friends",
                "They can join your lobbies straight from a notification.")
            + buildWelcomeStep("03", "Race the clock",
                "Type the title or artist faster than everyone else.")
            + "</table>"
            + "<a href=\"" + sessionUrl + "\" style=\"display:inline-block;"
            + "background:#c9f062;color:#1f2a08;border:1.5px solid #c9f062;"
            + "padding:13px 22px;border-radius:8px;font-size:14px;font-weight:600;"
            + "text-decoration:none\">Host a session &rarr;</a>"
            + "<div style=\"font-size:12px;color:#86807a;line-height:1.6;margin-top:18px;"
            + "padding-top:18px;border-top:1px dashed #e9e3d8\">"
            + "Reply to this email if you have any feedback — "
            + "it goes directly to me. Have fun! — Cyprien</div>"
            + "</td></tr>"
            + "<tr><td style=\"background:#fff;padding:0 28px 28px\">"
            + "<div style=\"text-align:center;font-size:11.5px;color:#86807a;line-height:1.7\">"
            + "<div>Blindtest &middot; A real-time music game &middot; "
            + "You're getting this because you have an account.</div>"
            + "<div style=\"margin-top:4px\">&copy; 2026 Blindtest</div>"
            + "</div></td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    private String buildWelcomeStep(String number, String title, String description) {
        return "<tr><td style=\"padding:0 0 10px\">"
            + "<table width=\"100%\" style=\"background:#faf7f1;border:1px solid #e9e3d8;"
            + "border-radius:10px;border-collapse:collapse\">"
            + "<tr><td style=\"padding:14px;width:32px;vertical-align:top\">"
            + "<span style=\"font-family:'JetBrains Mono',ui-monospace,monospace;"
            + "font-size:13px;color:#86807a;font-weight:700\">" + number + "</span></td>"
            + "<td style=\"padding:14px 14px 14px 0;vertical-align:top\">"
            + "<div style=\"font-size:14px;font-weight:700;margin-bottom:2px\">"
            + escapeHtml(title) + "</div>"
            + "<div style=\"font-size:13px;color:#4a4640;line-height:1.55\">"
            + escapeHtml(description) + "</div>"
            + "</td></tr></table></td></tr>";
    }

    private String buildPasswordResetEmailHtml(String username, String link) {
        return "<!DOCTYPE html>"
            + "<html lang=\"en\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>Reset your password</title></head>"
            + "<body style=\"margin:0;padding:0;background:#f5f1ea;"
            + "font-family:'Manrope',-apple-system,system-ui,sans-serif;color:#1a1814\">"
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td align=\"center\" style=\"padding:40px 16px\">"
            + "<table width=\"100%\" style=\"max-width:560px;border-radius:14px;overflow:hidden;"
            + "border:1px solid #e9e3d8;box-shadow:0 18px 40px -22px rgba(0,0,0,.18)\">"
            + "<tr><td style=\"background:#22201c;padding:22px 28px\">"
            + "<table width=\"100%\"><tr>"
            + "<td><table><tr><td style=\"vertical-align:middle\">"
            + "<div style=\"width:26px;height:26px;background:#c9f062;border-radius:7px;"
            + "display:inline-block;vertical-align:middle;margin-right:10px\"></div>"
            + "<span style=\"color:#fff;font-weight:700;font-size:16px;"
            + "vertical-align:middle\">Blindtest</span></td></tr></table></td>"
            + "<td align=\"right\"><span style=\"font-size:10.5px;font-weight:700;"
            + "letter-spacing:.08em;padding:4px 8px;border-radius:4px;"
            + "background:#f06262;color:#fff;text-transform:uppercase\">RESET</span></td>"
            + "</tr></table></td></tr>"
            + "<tr><td style=\"background:#fff;padding:32px 28px 28px\">"
            + "<h1 style=\"font-size:24px;font-weight:700;letter-spacing:-.015em;"
            + "margin:0 0 14px;line-height:1.2\">Reset your password, "
            + escapeHtml(username) + "</h1>"
            + "<p style=\"font-size:14.5px;line-height:1.65;color:#1a1814;margin:0 0 16px\">"
            + "Click the button below to choose a new password. This link expires in 1 hour.</p>"
            + "<div style=\"margin:24px 0 12px\">"
            + "<a href=\"" + link + "\" style=\"display:inline-block;background:#c9f062;"
            + "color:#1f2a08;border:1.5px solid #c9f062;padding:13px 22px;border-radius:8px;"
            + "font-size:14px;font-weight:600;text-decoration:none\">Reset password &rarr;</a>"
            + "</div>"
            + "<div style=\"font-size:12px;color:#86807a;line-height:1.6;margin-top:18px;"
            + "padding-top:18px;border-top:1px dashed #e9e3d8\">"
            + "If you didn't request a password reset, you can ignore this email safely.</div>"
            + "</td></tr>"
            + "<tr><td style=\"background:#fff;padding:0 28px 28px\">"
            + "<div style=\"text-align:center;font-size:11.5px;color:#86807a;line-height:1.7\">"
            + "<div>Blindtest &middot; A real-time music game</div>"
            + "<div style=\"margin-top:4px\">&copy; 2026 Blindtest</div>"
            + "</div></td></tr>"
            + "</table></td></tr></table></body></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
