package com.bingo.bingo_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BingoWebSocketHandler bingoHandler;

    public WebSocketConfig(BingoWebSocketHandler bingoHandler) {
        this.bingoHandler = bingoHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(bingoHandler, "/ws/**")
                .setAllowedOrigins("*");
    }
}