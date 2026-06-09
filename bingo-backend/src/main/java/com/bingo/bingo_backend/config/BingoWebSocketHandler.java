package com.bingo.bingo_backend.config;
 
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.bingo.bingo_backend.service.GameService;
import com.bingo.bingo_backend.service.LobbyService;
import com.fasterxml.jackson.databind.ObjectMapper;
 
@Component
public class BingoWebSocketHandler extends TextWebSocketHandler {
 
    private final LobbyService lobbyService;
    private final GameService gameService;
    private final ObjectMapper mapper = new ObjectMapper();
 
    public BingoWebSocketHandler(LobbyService lobbyService, GameService gameService) {
        this.lobbyService = lobbyService;
        this.gameService = gameService;
    }
 
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        String playerName = path.substring(path.lastIndexOf('/') + 1);
        session.getAttributes().put("playerName", playerName);
 
        if (path.contains("/ws/lobby/")) {
            lobbyService.connect(session, playerName);
        } else if (path.contains("/ws/juego/")) {
            gameService.connect(session, playerName);
        }
    }
 
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String path = session.getUri().getPath();
        String playerName = (String) session.getAttributes().get("playerName");
        String payload = message.getPayload();
 
        if (payload.equals("PING")) {
            synchronized (session) {
                session.sendMessage(new TextMessage("PONG"));
            }
            return;
        }
 
        try {
            if (path.contains("/ws/lobby/")) {
                Map<String, Object> data = mapper.readValue(payload, Map.class);
                String tipo = (String) data.get("tipo");
                if ("solicitar_inicio".equals(tipo)) {
                    lobbyService.solicitarInicio(playerName);
                } else if ("responder_inicio".equals(tipo)) {
                    boolean acepta = (Boolean) data.get("acepta");
                    lobbyService.responderInicio(playerName, acepta);
                }
            } else if (path.contains("/ws/juego/")) {
                Map<String, Object> data = mapper.readValue(payload, Map.class);
                String tipo = (String) data.get("tipo");
                if ("modo_juego".equals(tipo)) {
                    gameService.setMode(playerName, (String) data.get("mode"));
                } else if ("claim_bingo".equals(tipo)) {
                    gameService.handleBingoClaim(playerName, data);
                }
            }
        } catch (Exception ignored) {}
    }
 
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String path = session.getUri().getPath();
        String playerName = (String) session.getAttributes().get("playerName");
        if (path.contains("/ws/lobby/")) {
            lobbyService.disconnect(playerName);
        } else if (path.contains("/ws/juego/")) {
            gameService.disconnect(playerName);
        }
    }
}