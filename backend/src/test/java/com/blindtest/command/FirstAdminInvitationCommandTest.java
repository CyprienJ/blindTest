package com.blindtest.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.InvitationEntity;
import com.blindtest.service.FirstAdminInvitationService;
import com.blindtest.service.InvitationIssuer.IssuedInvitation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirstAdminInvitationCommandTest {

    @Mock
    private FirstAdminInvitationService service;
    @InjectMocks
    private FirstAdminInvitationCommand command;

    @Test
    void printsTheRegistrationLinkOnlyToTheOperatorTerminal() {
        String link = "https://nosblindtests.fr/auth/register?invite=secret";
        when(service.invite("admin@example.com")).thenReturn(
                Optional.of(new IssuedInvitation(new InvitationEntity(), "secret", link)));

        CommandExecution execution = run("  admin@example.com  \n");

        assertThat(execution.exitCode()).isEqualTo(FirstAdminInvitationCommand.EXIT_SUCCESS);
        assertThat(execution.output()).contains(link).contains("provision-first-admin");
        verify(service).invite("admin@example.com");
    }

    @Test
    void refusesWhenTheServiceDeclines() {
        when(service.invite("admin@example.com")).thenReturn(Optional.empty());

        CommandExecution execution = run("admin@example.com\n");

        assertThat(execution.exitCode()).isEqualTo(FirstAdminInvitationCommand.EXIT_REFUSED);
        assertThat(execution.output()).contains("an administrator already exists");
    }

    @Test
    void refusesInvalidInput() {
        CommandExecution execution = run("not-an-email\n");

        assertThat(execution.exitCode()).isEqualTo(FirstAdminInvitationCommand.EXIT_REFUSED);
        assertThat(execution.output()).contains("a valid email is required");
        verifyNoInteractions(service);
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
