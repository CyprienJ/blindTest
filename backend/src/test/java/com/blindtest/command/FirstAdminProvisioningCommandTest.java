package com.blindtest.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.AdminProvisioningOutcome;
import com.blindtest.service.FirstAdminProvisioningResult;
import com.blindtest.service.FirstAdminProvisioningService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirstAdminProvisioningCommandTest {

    @Mock
    private FirstAdminProvisioningService service;
    @InjectMocks
    private FirstAdminProvisioningCommand command;

    @Test
    void readsEmailFromStandardInputWithoutPrintingIt() {
        UUID userId = UUID.randomUUID();
        when(service.provision("admin@example.com")).thenReturn(
                new FirstAdminProvisioningResult(
                        AdminProvisioningOutcome.PROMOTED, userId));

        CommandExecution execution = run("  admin@example.com  \n");

        assertThat(execution.exitCode()).isEqualTo(FirstAdminProvisioningCommand.EXIT_SUCCESS);
        assertThat(execution.output()).contains("First administrator provisioned.");
        assertThat(execution.output()).contains(userId.toString());
        assertThat(execution.output()).doesNotContain("admin@example.com");
        verify(service).provision("admin@example.com");
    }

    @Test
    void treatsAlreadyAdminAsSuccessfulIdempotentExecution() {
        UUID userId = UUID.randomUUID();
        when(service.provision("admin@example.com")).thenReturn(
                new FirstAdminProvisioningResult(
                        AdminProvisioningOutcome.ALREADY_ADMIN, userId));

        CommandExecution execution = run("admin@example.com\n");

        assertThat(execution.exitCode()).isEqualTo(FirstAdminProvisioningCommand.EXIT_SUCCESS);
        assertThat(execution.output()).contains("already the sole administrator");
    }

    @Test
    void returnsRefusalExitCodeForUnsafeOrInvalidAttempts() {
        CommandExecution invalid = run("\n");

        assertThat(invalid.exitCode()).isEqualTo(FirstAdminProvisioningCommand.EXIT_REFUSED);
        assertThat(invalid.output()).contains("valid account email is required");
    }

    private CommandExecution run(String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exitCode = command.run(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return new CommandExecution(exitCode, bytes.toString(StandardCharsets.UTF_8));
    }

    private record CommandExecution(int exitCode, String output) {
    }
}
