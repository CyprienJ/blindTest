package com.blindtest.command;

import java.io.InputStream;
import java.io.PrintStream;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts and closes the minimal command context. */
public final class FirstAdminProvisioningLauncher {

    private FirstAdminProvisioningLauncher() {
    }

    /** Launches the interactive command with the configured database connection. */
    public static int launch(InputStream input, PrintStream output) {
        try (ConfigurableApplicationContext context =
                     new SpringApplicationBuilder(FirstAdminProvisioningCliConfiguration.class).
                             profiles("first-admin-cli").
                             web(WebApplicationType.NONE).
                             bannerMode(org.springframework.boot.Banner.Mode.OFF).
                             logStartupInfo(false).
                             run()) {
            return context.getBean(FirstAdminProvisioningCommand.class).run(input, output);
        }
    }
}
