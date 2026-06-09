package com.bingo.bingo_backend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LobbyService {

    private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();
    private final Map<String, Boolean> startVotes = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean gameStarting = false;
    private volatile String startRequester = null;

    public void connect(WebSocketSession session, String playerName) {
        connections.put(playerName, session);
        broadcastStatus();
        if (connections.size() >= 10 && !gameStarting) {
            iniciarPartida();
        }
    }

    public void disconnect(String playerName) {
        connections.remove(playerName);
        if (playerName.equals(startRequester)) {
            cancelarVotacion("El jugador que propuso el inicio se desconectó.");
        }
        startVotes.remove(playerName);
        if (connections.size() < 2) gameStarting = false;
        broadcastStatus();
    }

    public void solicitarInicio(String requester) {
        if (gameStarting || connections.size() < 2) return;
        if (startRequester != null) {
            send(requester, Map.of("tipo", "aviso", "mensaje", "Ya hay una votación en curso. Espera."));
            return;
        }
        startRequester = requester;
        startVotes.clear();
        startVotes.put(requester, true);

        List<String> otros = connections.keySet().stream()
                .filter(n -> !n.equals(requester)).toList();

        for (String name : otros) {
            send(name, Map.of(
                "tipo", "pregunta_inicio",
                "solicitante", requester.replace("_", " "),
                "mensaje", requester.replace("_", " ") + " quiere iniciar la partida. ¿Deseas iniciar?"
            ));
        }
        if (otros.isEmpty()) iniciarPartida();
    }

    public void responderInicio(String playerName, boolean acepta) {
        if (startRequester == null) return;
        startVotes.put(playerName, acepta);
        if (!acepta) {
            cancelarVotacion(playerName.replace("_", " ") + " no quiere iniciar todavía.");
            return;
        }
        if (startVotes.keySet().containsAll(connections.keySet()) &&
                startVotes.values().stream().allMatch(v -> v)) {
            iniciarPartida();
        }
    }

    private void cancelarVotacion(String motivo) {
        startRequester = null;
        startVotes.clear();
        for (String name : connections.keySet()) {
            send(name, Map.of("tipo", "inicio_cancelado", "mensaje", motivo));
        }
        broadcastStatus();
    }

    private void iniciarPartida() {
        if (gameStarting) return;
        gameStarting = true;
        startRequester = null;
        startVotes.clear();
        for (WebSocketSession session : connections.values()) {
            sendRaw(session, Map.of("action", "START_GAME"));
        }
        connections.clear();
        gameStarting = false;
    }

    private void broadcastStatus() {
        List<String> jugadores = new ArrayList<>(connections.keySet());
        int total = jugadores.size();
        Map<String, Object> payload = new HashMap<>();
        payload.put("tipo", "actualizacion_sala");
        payload.put("total", total);
        payload.put("jugadores", jugadores);
        payload.put("puede_iniciar", total >= 2 && total < 10 && !gameStarting);

        List<String> disconnected = new ArrayList<>();
        for (Map.Entry<String, WebSocketSession> entry : connections.entrySet()) {
            try {
                synchronized (entry.getValue()) {
                    entry.getValue().sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
                }
            } catch (Exception e) {
                disconnected.add(entry.getKey());
            }
        }
        disconnected.forEach(connections::remove);
    }

    private void send(String playerName, Map<String, ?> payload) {
        WebSocketSession session = connections.get(playerName);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
                }
            } catch (Exception ignored) {}
        }
    }

    private void sendRaw(WebSocketSession session, Map<String, ?> payload) {
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
                }
            } catch (Exception ignored) {}
        }
    }
}