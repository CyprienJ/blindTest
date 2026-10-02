package com.blindtest.service;

import java.util.Locale;
import java.util.Objects;
import org.springframework.context.MessageSource;

/**
 * Renders the transactional emails in one locale from the {@code i18n/emails} message bundle.
 * Every translated text is HTML-escaped; layout and colors are shared by all emails.
 */
final class EmailTemplates {

    /** Subject line and HTML body of one email. */
    record RenderedEmail(String subject, String html) {
    }

    private final MessageSource messages;
    private final String appBaseUrl;
    private final Locale locale;

    EmailTemplates(MessageSource messages, String appBaseUrl, Locale locale) {
        this.messages = Objects.requireNonNull(messages, "messages are required");
        this.appBaseUrl = Objects.requireNonNull(appBaseUrl, "appBaseUrl is required");
        this.locale = Objects.requireNonNull(locale, "locale is required");
    }

    RenderedEmail verification(String username, String link) {
        String body = paragraph(text("verification.body"), "#1a1814")
            + button(link, text("verification.button"))
            + copyLink(link)
            + note(text("verification.ignore"));
        return new RenderedEmail(text("verification.subject"), page(
            text("verification.title"), badge(text("verification.badge"), "#c9f062", "#1f2a08"),
            text("verification.heading", username), body, accountFooter(true)));
    }

    RenderedEmail passwordReset(String username, String link) {
        String body = paragraph(text("reset.body"), "#1a1814")
            + button(link, text("reset.button"))
            + note(text("reset.ignore"));
        return new RenderedEmail(text("reset.subject"), page(
            text("reset.title"), badge(text("reset.badge"), "#f06262", "#fff"),
            text("reset.heading", username), body, simpleFooter()));
    }

    RenderedEmail invitation(String inviterUsername, String link, long validHours) {
        String body = paragraph(text("invitation.body", validHours), "#1a1814")
            + button(link, text("invitation.button"))
            + copyLink(link)
            + note(text("invitation.ignore"));
        return new RenderedEmail(text("invitation.subject", inviterUsername), page(
            text("invitation.title"), badge(text("invitation.badge"), "#c9f062", "#1f2a08"),
            text("invitation.heading", inviterUsername), body, simpleFooter()));
    }

    RenderedEmail welcome(String username) {
        String body = paragraph(text("welcome.body"), "#1a1814")
            + "<table width=\"100%\" style=\"border-collapse:collapse;margin:20px 0\">"
            + step("01", text("welcome.step1.title"), text("welcome.step1.text"))
            + step("02", text("welcome.step2.title"), text("welcome.step2.text"))
            + step("03", text("welcome.step3.title"), text("welcome.step3.text"))
            + "</table>"
            + button(appBaseUrl + "/lobby/create", text("welcome.button"))
            + note(text("welcome.signoff"));
        return new RenderedEmail(text("welcome.subject"), page(
            text("welcome.title"), "", text("welcome.heading", username), body, accountFooter(false)));
    }

    private String text(String key, Object... arguments) {
        return messages.getMessage(key, arguments.length == 0 ? null : arguments, locale);
    }

    private String page(String title, String badge, String heading, String body, String footer) {
        return "<!DOCTYPE html>"
            + "<html lang=\"" + locale.getLanguage() + "\"><head><meta charset=\"UTF-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<title>" + escapeHtml(title) + "</title></head>"
            + "<body style=\"margin:0;padding:0;background:#f5f1ea;"
            + "font-family:'Manrope',-apple-system,system-ui,sans-serif;color:#1a1814\">"
            + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">"
            + "<tr><td align=\"center\" style=\"padding:40px 16px\">"
            + "<table width=\"100%\" style=\"max-width:560px;border-radius:14px;overflow:hidden;"
            + "border:1px solid #e9e3d8;box-shadow:0 18px 40px -22px rgba(0,0,0,.18)\">"
            // header
            + "<tr><td style=\"background:#22201c;padding:22px 28px\">"
            + "<table width=\"100%\"><tr>"
            + "<td><table><tr><td style=\"vertical-align:middle\">"
            + "<div style=\"width:26px;height:26px;background:#c9f062;border-radius:7px;"
            + "display:inline-block;vertical-align:middle;margin-right:10px\"></div>"
            + "<span style=\"color:#fff;font-weight:700;font-size:16px;"
            + "vertical-align:middle\">Blindtest</span></td></tr></table></td>"
            + badge
            + "</tr></table></td></tr>"
            // body
            + "<tr><td style=\"background:#fff;padding:32px 28px 28px\">"
            + "<h1 style=\"font-size:24px;font-weight:700;letter-spacing:-.015em;"
            + "margin:0 0 14px;line-height:1.2\">" + escapeHtml(heading) + "</h1>"
            + body
            + "</td></tr>"
            + footer
            + "</table></td></tr></table></body></html>";
    }

    private String badge(String label, String background, String color) {
        return "<td align=\"right\"><span style=\"font-size:10.5px;font-weight:700;"
            + "letter-spacing:.08em;padding:4px 8px;border-radius:4px;"
            + "background:" + background + ";color:" + color + ";text-transform:uppercase\">"
            + escapeHtml(label) + "</span></td>";
    }

    private String paragraph(String text, String color) {
        return "<p style=\"font-size:14.5px;line-height:1.65;color:" + color + ";margin:0 0 16px\">"
            + escapeHtml(text) + "</p>";
    }

    private String button(String link, String label) {
        return "<div style=\"margin:24px 0 12px\">"
            + "<a href=\"" + escapeHtml(link) + "\" style=\"display:inline-block;background:#c9f062;"
            + "color:#1f2a08;border:1.5px solid #c9f062;padding:13px 22px;border-radius:8px;"
            + "font-size:14px;font-weight:600;text-decoration:none\">" + escapeHtml(label) + "</a>"
            + "</div>";
    }

    private String copyLink(String link) {
        return paragraph(text("common.copy-link"), "#4a4640")
            + "<div style=\"padding:12px 14px;background:#faf7f1;border:1px solid #e9e3d8;"
            + "border-radius:8px;font-family:'JetBrains Mono',ui-monospace,monospace;"
            + "font-size:11.5px;color:#4a4640;word-break:break-all\">"
            + escapeHtml(link) + "</div>";
    }

    private String note(String text) {
        return "<div style=\"font-size:12px;color:#86807a;line-height:1.6;margin-top:18px;"
            + "padding-top:18px;border-top:1px dashed #e9e3d8\">" + escapeHtml(text) + "</div>";
    }

    private String step(String number, String title, String description) {
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

    private String accountFooter(boolean links) {
        String linkRow = links
            ? "<div style=\"margin-bottom:8px\">"
                + footerLink(appBaseUrl + "/settings", text("common.settings"), true)
                + footerLink(appBaseUrl + "/unsubscribe", text("common.unsubscribe"), true)
                + footerLink("mailto:hello@nosblindtests.fr", text("common.help"), false)
                + "</div>"
            : "";
        return footer(linkRow + "<div>Blindtest &middot; " + escapeHtml(text("common.tagline"))
            + " &middot; " + escapeHtml(text("common.account-notice")) + "</div>");
    }

    private String simpleFooter() {
        return footer("<div>Blindtest &middot; " + escapeHtml(text("common.tagline")) + "</div>");
    }

    private String footer(String content) {
        return "<tr><td style=\"background:#fff;padding:0 28px 28px\">"
            + "<div style=\"text-align:center;font-size:11.5px;color:#86807a;line-height:1.7\">"
            + content
            + "<div style=\"margin-top:4px\">&copy; 2026 Blindtest</div>"
            + "</div></td></tr>";
    }

    private String footerLink(String href, String label, boolean spaced) {
        return "<a href=\"" + escapeHtml(href) + "\" style=\"color:#4a4640;text-decoration:none"
            + (spaced ? ";margin-right:14px" : "") + "\">" + escapeHtml(label) + "</a>";
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
