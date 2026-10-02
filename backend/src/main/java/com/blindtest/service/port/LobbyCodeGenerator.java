package com.blindtest.service.port;

import com.blindtest.model.lobby.LobbyCode;

/** Generates candidate join codes; uniqueness is enforced by the lobby store. */
@FunctionalInterface
public interface LobbyCodeGenerator {

    LobbyCode next();
}
