package com.blindtest.websocket;

import com.blindtest.security.BearerTokenAuthenticator;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * STOMP channel interceptor that validates the account or guest JWT in the
 * {@code Authorization} header of every {@code CONNECT} frame and sets the authenticated principal on the accessor.
 */
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final BearerTokenAuthenticator authenticator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new MessageDeliveryException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        UsernamePasswordAuthenticationToken auth = authenticator.authenticate(token).
                orElseThrow(() -> new MessageDeliveryException("Invalid or expired token"));
        accessor.setUser(auth);
        return message;
    }
}
