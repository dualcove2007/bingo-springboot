package com.bingo.bingo_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseKey);
        headers.set("Authorization", "Bearer " + supabaseKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        RestTemplate rt = new RestTemplate();
        String username = body.get("username");

        // Verificar si ya existe
        String checkUrl = supabaseUrl + "/rest/v1/usuarios?username=eq." + username;
        ResponseEntity<List> check = rt.exchange(checkUrl, HttpMethod.GET,
                new HttpEntity<>(getHeaders()), List.class);

        if (check.getBody() != null && !check.getBody().isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("detail", "Ese usuario ya está registrado, bro."));
        }

        // Insertar
        String insertUrl = supabaseUrl + "/rest/v1/usuarios";
        Map<String, String> payload = Map.of(
            "username", username,
            "player_name", body.get("player_name"),
            "password", body.get("password")
        );
        rt.exchange(insertUrl, HttpMethod.POST,
                new HttpEntity<>(payload, getHeaders()), String.class);

        return ResponseEntity.ok(Map.of("message", "¡Usuario registrado con éxito!", "user", username));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        RestTemplate rt = new RestTemplate();
        String username = body.get("username");

        String url = supabaseUrl + "/rest/v1/usuarios?username=eq." + username;
        ResponseEntity<List> res = rt.exchange(url, HttpMethod.GET,
                new HttpEntity<>(getHeaders()), List.class);

        if (res.getBody() == null || res.getBody().isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("detail", "Ese usuario no existe, mano."));
        }

        Map<String, Object> user = (Map<String, Object>) res.getBody().get(0);
        if (!body.get("password").equals(user.get("password"))) {
            return ResponseEntity.status(401).body(Map.of("detail", "Contraseña incorrecta, pa."));
        }

        return ResponseEntity.ok(Map.of(
            "message", "¡Ingreso exitoso!",
            "player_name", user.get("player_name"),
            "username", user.get("username")
        ));
    }
}