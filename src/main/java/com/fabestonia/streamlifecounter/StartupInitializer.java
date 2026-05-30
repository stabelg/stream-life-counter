package com.fabestonia.streamlifecounter;

import com.fabestonia.streamlifecounter.tournament.TournamentService;
import com.fabestonia.streamlifecounter.tournament.TournamentState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class StartupInitializer {

    @Autowired private ConfigStore        config;
    @Autowired private GameState          gameState;
    @Autowired private TournamentState    tournamentState;
    @Autowired private TournamentService  tournamentService;

    @PostConstruct
    public void restoreState() {
        // ── Game state ────────────────────────────────────────────────────────
        String playerL = config.get("playerL");
        String playerR = config.get("playerR");
        String heroL   = config.get("heroL");
        String heroR   = config.get("heroR");
        String round   = config.get("round");
        String lifeL   = config.get("lifeL");
        String lifeR   = config.get("lifeR");

        if (playerL != null) gameState.setPlayerL(playerL);
        if (playerR != null) gameState.setPlayerR(playerR);
        if (heroL   != null) gameState.setHeroL(heroL);
        if (heroR   != null) gameState.setHeroR(heroR);
        if (round   != null) gameState.setRound(round);
        if (lifeL   != null) gameState.setLifeL(Integer.parseInt(lifeL));
        if (lifeR   != null) gameState.setLifeR(Integer.parseInt(lifeR));

        // ── Tournament ID ─────────────────────────────────────────────────────
        String tournamentId = config.get("tournamentId");
        if (tournamentId != null) {
            tournamentState.setTournamentId(tournamentId);
            tournamentState.setStatus("Restored — logging in…");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void autoLogin() {
        String username = config.get("username");
        String password = config.get("password");
        if (username == null || password == null) return;

        Thread thread = new Thread(() -> {
            boolean ok = tournamentService.login(username, password);
            if (ok) {
                tournamentService.fetchHeroes();
                tournamentService.fetchAll();
            }
        }, "auto-login");
        thread.setDaemon(true);
        thread.start();
    }
}
