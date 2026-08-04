package com.blindtest.model.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessedCommandLedgerTest {

    @Test
    void retainsAUniqueBoundedWindowOfRecentCommands() {
        ProcessedCommandLedger ledger = ProcessedCommandLedger.empty();
        List<UUID> commands = new ArrayList<>();
        for (int index = 0; index <= ProcessedCommandLedger.MAXIMUM_RETAINED_COMMANDS; index++) {
            UUID commandId = new UUID(0, index + 1L);
            commands.add(commandId);
            ledger = ledger.record(commandId);
        }

        assertThat(ledger.commandIds()).hasSize(
                ProcessedCommandLedger.MAXIMUM_RETAINED_COMMANDS);
        assertThat(ledger.contains(commands.getFirst())).isFalse();
        assertThat(ledger.contains(commands.get(1))).isTrue();
        assertThat(ledger.record(commands.getLast())).isSameAs(ledger);
    }
}
