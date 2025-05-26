package org.example.speaknotebackend.config;

import org.example.speaknotebackend.websocket.AnnotationWebSocketHandler;
import org.example.speaknotebackend.websocket.AudioWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AnnotationWebSocketHandler annotationWebSocketHandler;
    private final AudioWebSocketHandler audioWebSocketHandler;

    @Value("${custom.cors.allowed-origin}")
    private String allowedOrigin;

    public WebSocketConfig(
            AnnotationWebSocketHandler annotationWebSocketHandler,
            AudioWebSocketHandler audioWebSocketHandler) {
        this.annotationWebSocketHandler = annotationWebSocketHandler;
        this.audioWebSocketHandler = audioWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(annotationWebSocketHandler, "/ws/annotation")
                .setAllowedOrigins(allowedOrigin);

        registry.addHandler(audioWebSocketHandler, "/ws/audio")
                .setAllowedOrigins(allowedOrigin);
    }
}
