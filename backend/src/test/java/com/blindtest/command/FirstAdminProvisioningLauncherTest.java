package com.blindtest.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FirstAdminProvisioningLauncherTest {

    @Test
    void startsMinimalNonWebContextAndRunsCommand() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url",
                "jdbc:h2:mem:firstadminlauncher;DB_CLOSE_DELAY=-1");
        properties.put("spring.datasource.driver-class-name", "org.h2.Driver");
        properties.put("spring.datasource.username", "sa");
        properties.put("spring.datasource.password", "");
        properties.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        properties.put("spring.flyway.enabled", "false");
        Map<String, String> previous = setSystemProperties(properties);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            int exitCode = FirstAdminProvisioningLauncher.launch(
                    new ByteArrayInputStream(
                            "missing@example.com\n".getBytes(StandardCharsets.UTF_8)),
                    new PrintStream(bytes, true, StandardCharsets.UTF_8));

            assertThat(exitCode).isEqualTo(FirstAdminProvisioningCommand.EXIT_REFUSED);
            assertThat(bytes.toString(StandardCharsets.UTF_8)).
                    contains("Provisioning refused: account not found.").
                    doesNotContain("missing@example.com");
        } finally {
            restoreSystemProperties(previous);
        }
    }

    private Map<String, String> setSystemProperties(Map<String, String> properties) {
        Map<String, String> previous = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            previous.put(key, System.getProperty(key));
            System.setProperty(key, value);
        });
        return previous;
    }

    private void restoreSystemProperties(Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }
}
