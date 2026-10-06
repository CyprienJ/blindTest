package com.blindtest.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blindtest.model.entity.UserRole;
import com.blindtest.security.BearerTokenAuthenticator;
import com.blindtest.security.GuestToken;
import com.blindtest.security.JwtTokenProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class JwtChannelInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        interceptor = new JwtChannelInterceptor(new BearerTokenAuthenticator(jwtTokenProvider));
    }

    @Test
    void preSendSetsUserOnConnectWithValidToken() {
        UUID userId = UUID.randomUUID();
        String token = "valid-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.extractUserId(token)).thenReturn(userId);
        when(jwtTokenProvider.extractRole(token)).thenReturn(UserRole.ADMIN);

        Message<byte[]> message = buildConnectMessage("Bearer " + token);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) resultAccessor.getUser();
        assertThat(auth.getName()).isEqualTo(userId.toString());
    }

    @Test
    void preSendAuthenticatesGuestsAsTheirParticipant() {
        UUID sessionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        String token = "guest-token";
        when(jwtTokenProvider.parseGuestToken(token)).
                thenReturn(Optional.of(new GuestToken(sessionId, participantId)));

        Message<?> result = interceptor.preSend(buildConnectMessage("Bearer " + token), null);

        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) StompHeaderAccessor.wrap(result).getUser();
        assertThat(auth.getName()).isEqualTo(participantId.toString());
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_GUEST");
        assertThat(auth.getDetails()).isEqualTo(new GuestToken(sessionId, participantId));
    }

    @Test
    void preSendRejectsConnectWithInvalidToken() {
        String token = "invalid-token";
        when(jwtTokenProvider.validateToken(token)).thenReturn(false);

        Message<byte[]> message = buildConnectMessage("Bearer " + token);

        assertThatThrownBy(() -> interceptor.preSend(message, null)).
                isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void preSendRejectsConnectWithMissingAuthHeader() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null)).
                isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void preSendPassesThroughNonConnectMessages() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    private Message<byte[]> buildConnectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", authHeader);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
