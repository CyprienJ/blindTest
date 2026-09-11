package com.blindtest.testing.e2e;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the captured-email inbox so full-stack browser specs can read verification and
 * password-reset tokens without a real mailbox. Active only under the {@code e2e} Spring
 * profile, which no production or development deployment sets.
 */
@RestController
@Profile("e2e")
@RequiredArgsConstructor
public class E2eTestSupportController {

    private final FakeEmailSender fakeEmailSender;

    /** Returns every captured email sent to {@code to}, oldest first. */
    @GetMapping("/api/e2e/emails")
    public List<FakeEmailSender.CapturedEmail> emailsSentTo(@RequestParam String to) {
        return fakeEmailSender.messages().stream().
                filter(email -> email.toEmail().equalsIgnoreCase(to)).
                toList();
    }

    /** Clears the captured inbox so an independent spec file starts from a clean state. */
    @DeleteMapping("/api/e2e/emails")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetEmails() {
        fakeEmailSender.reset();
    }
}
