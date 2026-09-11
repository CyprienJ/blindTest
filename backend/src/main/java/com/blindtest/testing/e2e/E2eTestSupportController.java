package com.blindtest.testing.e2e;

import com.blindtest.service.FirstAdminProvisioningResult;
import com.blindtest.service.FirstAdminProvisioningService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes test-only backdoors so full-stack browser specs can drive the application without a
 * real mailbox or the interactive first-administrator CLI. Active only under the {@code e2e}
 * Spring profile, which no production or development deployment sets.
 */
@RestController
@Profile("e2e")
@RequiredArgsConstructor
public class E2eTestSupportController {

    private final FakeEmailSender fakeEmailSender;
    private final FirstAdminProvisioningService firstAdminProvisioningService;

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

    /**
     * Promotes the verified account to administrator, reusing the same
     * {@link FirstAdminProvisioningService} boundary as the operator-run CLI so browser specs
     * can reach admin-only screens without a terminal attached to the container.
     */
    @PostMapping("/api/e2e/admin-provisioning")
    public FirstAdminProvisioningResult provisionFirstAdmin(@RequestParam String email) {
        return firstAdminProvisioningService.provision(email);
    }
}
