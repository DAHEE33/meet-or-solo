package com.survey.meetorsolo.global.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthenticationInterceptor authenticationInterceptor;
    private final WebSocketPrincipalHandshakeHandler handshakeHandler;
    private final WebSocketInboundChannelInterceptor inboundChannelInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthenticationInterceptor authenticationInterceptor,
            WebSocketPrincipalHandshakeHandler handshakeHandler,
            WebSocketInboundChannelInterceptor inboundChannelInterceptor,
            @Value("${app.cors.allowed-origins:}") String allowedOrigins
    ) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.inboundChannelInterceptor = inboundChannelInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        var endpoint = registry.addEndpoint("/ws")
                .addInterceptors(authenticationInterceptor)
                .setHandshakeHandler(handshakeHandler);
        if (allowedOrigins.length > 0) {
            endpoint.setAllowedOrigins(allowedOrigins);
        }
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundChannelInterceptor);
    }
}
