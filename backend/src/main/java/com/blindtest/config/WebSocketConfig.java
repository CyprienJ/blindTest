package com.blindtest.config;

import com.blindtest.websocket.JwtChannelInterceptor;
import com.blindtest.websocket.SessionDestinationAuthorizationInterceptor;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** Configures the STOMP message broker and WebSocket endpoint with JWT authentication. */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final SessionDestinationAuthorizationInterceptor sessionAuthorizationInterceptor;
    private final String allowedOrigin;

    /** Constructs the WebSocket configuration with the same origin policy as REST. */
    public WebSocketConfig(
            JwtChannelInterceptor jwtChannelInterceptor,
            SessionDestinationAuthorizationInterceptor sessionAuthorizationInterceptor,
            @Value("${cors.allowed-origins:${app.base-url:http://localhost:4200}}")
                    String allowedOrigins) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
        this.sessionAuthorizationInterceptor = sessionAuthorizationInterceptor;
        this.allowedOrigin = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigin.split(",")).
                map(String::trim).
                filter(origin -> !origin.isEmpty()).
                toArray(String[]::new);
        registry.addEndpoint("/ws").
                setAllowedOrigins(origins);
        registry.addEndpoint("/ws-sockjs").
                setAllowedOrigins(origins).
                withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor, sessionAuthorizationInterceptor);
    }
}
