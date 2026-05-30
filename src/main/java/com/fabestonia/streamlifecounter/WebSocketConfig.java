package com.fabestonia.streamlifecounter;

import com.fabestonia.streamlifecounter.tournament.TournamentWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private StreamWebSocketHandler streamWebSocketHandler;

    @Autowired
    private TournamentWebSocketHandler tournamentWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(streamWebSocketHandler, "/ws")
                .setAllowedOrigins("*");
        registry.addHandler(tournamentWebSocketHandler, "/ws/tournament")
                .setAllowedOrigins("*");
    }
}
