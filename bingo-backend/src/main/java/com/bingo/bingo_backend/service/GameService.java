package com.bingo.bingo_backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GameService {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> playerModes = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean gameActive = false;
    private List<Map<String, Object>> calledBalls = new ArrayList<>();
    private ScheduledExecutorService ballScheduler;

    public void connect(WebSocketSession session, String playerName) {
        sessions.put(playerName, session);
        playerModes.put(playerName, null);
    }

    public void disconnect(String playerName) {
        sessions.remove(playerName);
        playerModes.remove(playerName);
        if (gameActive && sessions.size() == 1) {
            String winner = sessions.keySet().iterator().next();
            send(winner, Map.of(
                "tipo", "resultado_bingo",
                "ganaste", true,
                "ganador", winner.replace("_", " ")
            ));
            reset();
        }
    }

    public void setMode(String playerName, String mode) {
        if (!sessions.containsKey(playerName)) return;
        playerModes.put(playerName, mode);

        List<String> names = new ArrayList<>(sessions.keySet());
        List<String> modes = names.stream().map(playerModes::get).toList();

        if (modes.stream().allMatch(m -> m != null)) {
            long distinctModes = modes.stream().distinct().count();
            if (distinctModes == 1) {
                for (String n : names) {
                    send(n, Map.of("tipo", "modo_ok", "mode", modes.get(0)));
                }
                if (!gameActive) {
                    gameActive = true;
                    calledBalls = generateBalls();
                    startBallBroadcast();
                }
            } else {
                for (String n : names) {
                    String myMode = playerModes.get(n);
                    String opponentMode = names.stream()
                        .filter(o -> !o.equals(n) && !playerModes.get(o).equals(myMode))
                        .map(playerModes::get)
                        .findFirst().orElse(null);
                    if (opponentMode != null) {
                        send(n, Map.of(
                            "tipo", "modo_mismatch",
                            "my_mode", myMode,
                            "opponent_mode", opponentMode
                        ));
                    }
                }
            }
        }
    }

    public void handleBingoClaim(String claimer, Map<String, Object> data) {
        if (!gameActive) return;

        List<String> marked = (List<String>) data.get("marked");
        List<Integer> called = (List<Integer>) data.get("called");
        List<List<Integer>> card = (List<List<Integer>>) data.get("card");
        String mode = (String) data.get("mode");

        boolean valid = validateBingo(new HashSet<>(marked), new HashSet<>(called), card, mode);

        if (valid) {
            gameActive = false;
            if (ballScheduler != null) ballScheduler.shutdownNow();
            for (String name : sessions.keySet()) {
                send(name, Map.of(
                    "tipo", "resultado_bingo",
                    "ganaste", name.equals(claimer),
                    "ganador", claimer.replace("_", " ")
                ));
            }
            reset();
        } else {
            send(claimer, Map.of(
                "tipo", "bingo_invalido",
                "mensaje", "Tu cartón no cumple las condiciones todavía. ¡Sigue jugando!"
            ));
        }
    }

    private boolean validateBingo(Set<String> marked, Set<Integer> called, List<List<Integer>> card, String mode) {
        List<int[]> pattern = getPattern(mode);
        for (int[] pos : pattern) {
            int col = pos[0], row = pos[1];
            if (!marked.contains(col + "-" + row)) return false;
            if (!called.contains(card.get(col).get(row))) return false;
        }
        return true;
    }

    private List<int[]> getPattern(String mode) {
        List<int[]> positions = new ArrayList<>();
        for (int col = 0; col < 5; col++) {
            for (int row = 0; row < 5; row++) {
                if (col == 2 && row == 2) continue;
                boolean include = false;
                switch (mode) {
                    case "carton_lleno" -> include = true;
                    case "en_x" -> include = (col == row) || (col + row == 4);
                    case "en_o" -> include = (row == 0 || row == 4 || col == 0 || col == 4);
                    case "en_l" -> include = (col == 0 || row == 4);
                }
                if (include) positions.add(new int[]{col, row});
            }
        }
        return positions;
    }

    private List<Map<String, Object>> generateBalls() {
        Map<String, int[]> columns = new LinkedHashMap<>();
        columns.put("B", new int[]{1, 15});
        columns.put("I", new int[]{16, 30});
        columns.put("N", new int[]{31, 45});
        columns.put("G", new int[]{46, 60});
        columns.put("O", new int[]{61, 75});

        List<Map<String, Object>> balls = new ArrayList<>();
        for (Map.Entry<String, int[]> entry : columns.entrySet()) {
            for (int n = entry.getValue()[0]; n <= entry.getValue()[1]; n++) {
                balls.add(Map.of("letra", entry.getKey(), "numero", n));
            }
        }
        Collections.shuffle(balls);
        return balls;
    }

    private void startBallBroadcast() {
        ballScheduler = Executors.newSingleThreadScheduledExecutor();
        final int[] index = {0};
        ballScheduler.scheduleAtFixedRate(() -> {
            if (!gameActive || index[0] >= calledBalls.size()) {
                ballScheduler.shutdownNow();
                return;
            }
            Map<String, Object> ball = calledBalls.get(index[0]++);
            Map<String, Object> payload = new HashMap<>(ball);
            payload.put("tipo", "balota");
            for (String name : sessions.keySet()) {
                send(name, payload);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void send(String playerName, Map<String, ?> payload) {
        WebSocketSession session = sessions.get(playerName);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
                }
            } catch (Exception ignored) {}
        }
    }

    private void reset() {
        sessions.clear();
        playerModes.clear();
        calledBalls.clear();
        gameActive = false;
        if (ballScheduler != null) ballScheduler.shutdownNow();
    }
}