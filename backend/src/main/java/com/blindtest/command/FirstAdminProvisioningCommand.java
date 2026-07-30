package com.blindtest.command;

import com.blindtest.model.entity.AdminProvisioningOutcome;
import com.blindtest.service.FirstAdminProvisioningResult;
import com.blindtest.service.FirstAdminProvisioningService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Interactive operator command that provisions the first administrator. */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirstAdminProvisioningCommand {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_REFUSED = 2;

    private final FirstAdminProvisioningService provisioningService;

    /** Reads one email without echoing it back and returns a process exit code. */
    public int run(InputStream input, PrintStream output) {
        output.print("Verified account email: ");
        output.flush();
        String email;
        try {
            email = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).readLine();
        } catch (IOException exception) {
            log.warn("First administrator provisioning outcome=INPUT_ERROR");
            output.println();
            output.println("Provisioning refused: input could not be read.");
            return EXIT_REFUSED;
        }
        if (email == null || email.isBlank() || email.length() > 255) {
            log.warn("First administrator provisioning outcome=INVALID_INPUT");
            output.println("Provisioning refused: a valid account email is required.");
            return EXIT_REFUSED;
        }

        FirstAdminProvisioningResult result =
                provisioningService.provision(email.strip());
        return report(result, output);
    }

    private int report(FirstAdminProvisioningResult result, PrintStream output) {
        AdminProvisioningOutcome outcome = result.outcome();
        log.info("First administrator provisioning outcome={} targetUserId={}",
                outcome, result.targetUserId());
        return switch (outcome) {
            case PROMOTED -> success(
                    output, "First administrator provisioned.", result);
            case ALREADY_ADMIN -> success(
                    output, "Account is already the sole administrator.", result);
            case USER_NOT_FOUND -> refused(
                    output, "Provisioning refused: account not found.");
            case EMAIL_NOT_VERIFIED -> refused(
                    output, "Provisioning refused: account email is not verified.");
            case ADMIN_ALREADY_EXISTS -> refused(
                    output, "Provisioning refused: an administrator already exists.");
        };
    }

    private int success(
            PrintStream output, String message, FirstAdminProvisioningResult result) {
        output.println(message + " userId=" + result.targetUserId());
        return EXIT_SUCCESS;
    }

    private int refused(PrintStream output, String message) {
        output.println(message);
        return EXIT_REFUSED;
    }
}
