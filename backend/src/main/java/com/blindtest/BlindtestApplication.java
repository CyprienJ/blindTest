package com.blindtest;

import com.blindtest.command.FirstAdminProvisioningLauncher;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BlindtestApplication {

    private static final String FIRST_ADMIN_COMMAND = "provision-first-admin";

    /** Application entry point. */
    public static void main(String[] args) {
        if (Arrays.equals(args, new String[] {FIRST_ADMIN_COMMAND})) {
            int exitCode = FirstAdminProvisioningLauncher.launch(System.in, System.out);
            System.exit(exitCode);
            return;
        }
        SpringApplication.run(BlindtestApplication.class, args);
    }
}
