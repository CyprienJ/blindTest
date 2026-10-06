package com.blindtest.websocket;

import com.blindtest.model.session.ActiveSessionSnapshot;
import com.blindtest.model.session.SessionId;
import com.blindtest.security.GuestToken;
import com.blindtest.store.LobbyStore;
import com.blindtest.store.SessionStore;
import com.blindtest.websocket.protocol.SessionProtocol;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Authorizes gameplay commands and subscriptions against session or lobby membership. */
@Component
@RequiredArgsConstructor
public class SessionDestinationAuthorizationInterceptor implements ChannelInterceptor {

    private static final String SESSION_PATH_MARKER = "/sessions";

    private final SessionStore sessionStore;
    private final LobbyStore lobbyStore;
    private final Clock clock;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !isDestinationCommand(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null || !destination.contains(SESSION_PATH_MARKER)) {
            return message;
        }

        UUID sessionId = authorizedSessionId(accessor.getCommand(), destination);
        UUID principalId = authenticatedPrincipalId(accessor.getUser());
        if (!isGuestScopedTo(accessor.getUser(), sessionId)
                || !isSessionMember(new SessionId(sessionId), principalId)) {
            throw denied("Session is unavailable or access is denied");
        }
        return message;
    }

    /** Lobby members subscribe before the game session exists under the same ID. */
    private boolean isSessionMember(SessionId sessionId, UUID principalId) {
        Instant now = clock.instant();
        Optional<ActiveSessionSnapshot> session = sessionStore.find(sessionId, now);
        if (session.isPresent()) {
            return session.get().participants().stream().
                    anyMatch(participant -> participant.principalId().equals(principalId));
        }
        return lobbyStore.find(sessionId, now).
                flatMap(lobby -> lobby.memberFor(principalId)).
                isPresent();
    }

    /** A guest token only ever reaches the session it was issued for (ADR-0020). */
    private boolean isGuestScopedTo(Principal principal, UUID sessionId) {
        return !(principal instanceof Authentication authentication
                && authentication.getDetails() instanceof GuestToken guest)
                || guest.sessionId().equals(sessionId);
    }

    private boolean isDestinationCommand(StompCommand command) {
        return StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command);
    }

    private UUID authorizedSessionId(StompCommand command, String destination) {
        String prefix;
        if (StompCommand.SEND.equals(command)) {
            prefix = SessionProtocol.COMMAND_DESTINATION_PREFIX;
        } else if (destination.startsWith(SessionProtocol.PUBLIC_EVENT_DESTINATION_PREFIX)) {
            prefix = SessionProtocol.PUBLIC_EVENT_DESTINATION_PREFIX;
        } else if (destination.startsWith(SessionProtocol.PRIVATE_EVENT_DESTINATION_PREFIX)) {
            prefix = SessionProtocol.PRIVATE_EVENT_DESTINATION_PREFIX;
        } else {
            throw denied("Invalid gameplay destination");
        }
        if (!destination.startsWith(prefix)) {
            throw denied("Invalid gameplay destination");
        }
        String identifier = destination.substring(prefix.length());
        if (identifier.isEmpty() || identifier.contains("/")) {
            throw denied("Invalid gameplay destination");
        }
        try {
            UUID parsed = UUID.fromString(identifier);
            if (!parsed.toString().equalsIgnoreCase(identifier)) {
                throw denied("Invalid gameplay destination");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw denied("Invalid gameplay destination");
        }
    }

    private UUID authenticatedPrincipalId(Principal principal) {
        if (principal == null) {
            throw denied("Authentication is required");
        }
        String name = principal.getName();
        if (name == null) {
            throw denied("Authentication is required");
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException exception) {
            throw denied("Authentication is required");
        }
    }

    private MessageDeliveryException denied(String message) {
        return new MessageDeliveryException(message);
    }
}
