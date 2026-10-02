package com.blindtest.command;

import com.blindtest.service.FirstAdminInvitationService;
import com.blindtest.service.InvitationIssuer.IssuedInvitation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Interactive operator command that prints a registration link for the future first
 * administrator. The link is written only to the operator's terminal, never to logs or email.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirstAdminInvitationCommand {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_REFUSED = 2;

    private final FirstAdminInvitationService invitationService;

    /** Reads one email and returns a process exit code. */
    public int run(InputStream input, PrintStream output) {
        output.print("Future administrator email: ");
        output.flush();
        String email;
        try {
            email = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)).readLine();
        } catch (IOException exception) {
            log.warn("First administrator invitation outcome=INPUT_ERROR");
            output.println();
            output.println("Invitation refused: input could not be read.");
            return EXIT_REFUSED;
        }
        if (email == null || email.isBlank() || email.length() > 255 || !email.contains("@")) {
            log.warn("First administrator invitation outcome=INVALID_INPUT");
            output.println("Invitation refused: a valid email is required.");
            return EXIT_REFUSED;
        }

        Optional<IssuedInvitation> issued = invitationService.invite(email.strip());
        if (issued.isEmpty()) {
            log.info("First administrator invitation outcome=REFUSED");
            output.println("Invitation refused: an administrator already exists "
                    + "or this email already has an account.");
            return EXIT_REFUSED;
        }
        log.info("First administrator invitation outcome=ISSUED invitationId={}",
                issued.get().invitation().getId());
        output.println("Invitation issued. Open this link within 48 hours to create the account,");
        output.println("then run provision-first-admin to promote it:");
        output.println(issued.get().link());
        return EXIT_SUCCESS;
    }
}
