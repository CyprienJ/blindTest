package com.blindtest.websocket;

import com.blindtest.service.SessionGameplayService;
import com.blindtest.websocket.protocol.SessionCommand;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/** Thin STOMP boundary forwarding authorized gameplay commands to the session orchestrator. */
@Controller
@RequiredArgsConstructor
public class SessionCommandController {

    private final SessionGameplayService gameplayService;

    /** Handles every versioned command accepted for one member-authorized session. */
    @MessageMapping("/sessions/{sessionId}")
    public void handle(
            @DestinationVariable UUID sessionId,
            SessionCommand command,
            Principal principal) {
        gameplayService.handle(sessionId, UUID.fromString(principal.getName()), command);
    }
}
