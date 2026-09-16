package com.blindtest.model.session;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable bounded ledger used to make recent gameplay commands idempotent. */
public final class ProcessedCommandLedger {

    static final int MAXIMUM_RETAINED_COMMANDS = 1024;

    private static final ProcessedCommandLedger EMPTY = new ProcessedCommandLedger(List.of());

    private final List<UUID> commandIds;

    private ProcessedCommandLedger(List<UUID> commandIds) {
        Objects.requireNonNull(commandIds, "commandIds are required");
        if (commandIds.size() > MAXIMUM_RETAINED_COMMANDS
                || commandIds.stream().anyMatch(Objects::isNull)
                || new LinkedHashSet<>(commandIds).size() != commandIds.size()) {
            throw new IllegalArgumentException("processed command ledger is invalid");
        }
        this.commandIds = List.copyOf(commandIds);
    }

    /** Returns the shared empty command ledger. */
    public static ProcessedCommandLedger empty() {
        return EMPTY;
    }

    /** Returns whether this recent command was already incorporated into session state. */
    public boolean contains(UUID commandId) {
        return commandIds.contains(Objects.requireNonNull(commandId, "commandId is required"));
    }

    /** Appends one command, retaining only the most recent bounded window. */
    public ProcessedCommandLedger record(UUID commandId) {
        Objects.requireNonNull(commandId, "commandId is required");
        if (contains(commandId)) {
            return this;
        }
        List<UUID> updated = new ArrayList<>(commandIds);
        updated.add(commandId);
        if (updated.size() > MAXIMUM_RETAINED_COMMANDS) {
            updated.removeFirst();
        }
        return new ProcessedCommandLedger(updated);
    }

    /** Returns an immutable oldest-to-newest projection for diagnostics and tests. */
    public List<UUID> commandIds() {
        return commandIds;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ProcessedCommandLedger that
                && commandIds.equals(that.commandIds);
    }

    @Override
    public int hashCode() {
        return commandIds.hashCode();
    }
}
