package com.blindtest.infra;

import java.io.IOException;
import java.util.List;

/** Factory that creates OS processes from a command list. Extracted to allow mocking in tests. */
@FunctionalInterface
public interface ProcessFactory {

    /** Starts a new process for the given command. */
    Process start(List<String> command) throws IOException;
}
