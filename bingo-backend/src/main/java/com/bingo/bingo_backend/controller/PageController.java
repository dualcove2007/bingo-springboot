package com.bingo.bingo_backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String login() { return "login"; }

    @GetMapping("/registro")
    public String registro() { return "registro"; }

    @GetMapping("/menu")
    public String menu() { return "menu"; }

    @GetMapping("/opciones")
    public String opciones() { return "opciones"; }

    @GetMapping("/lobby")
    public String lobby() { return "lobby"; }

    @GetMapping("/juego")
    public String juego() { return "juego"; }

    @GetMapping("/ganador")
    public String ganador() { return "ganador"; }

    @GetMapping("/perdedor")
    public String perdedor() { return "perdedor"; }
}