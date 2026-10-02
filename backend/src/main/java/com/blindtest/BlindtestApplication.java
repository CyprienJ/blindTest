package com.blindtest;

import com.blindtest.command.FirstAdminProvisioningLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BlindtestApplication {

    private static final String FIRST_ADMIN_COMMAND = "provision-first-admin";
    private static final String FIRST_ADMIN_INVITATION_COMMAND = "invite-first-admin";

    /** Application entry point. */
    public static void main(String[] args) {
        String command = args.length == 1 ? args[0] : "";
        switch (command) {
            case FIRST_ADMIN_COMMAND ->
                    System.exit(FirstAdminProvisioningLauncher.launch(System.in, System.out));
            case FIRST_ADMIN_INVITATION_COMMAND ->
                    System.exit(FirstAdminProvisioningLauncher.launchInvitation(System.in, System.out));
            default -> SpringApplication.run(BlindtestApplication.class, args);
        }
    }
}
