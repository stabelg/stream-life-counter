package com.fabestonia.streamlifecounter.tournament;

import com.fabestonia.streamlifecounter.ConfigStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tournament")
public class TournamentController {

    @Autowired private TournamentService service;
    @Autowired private TournamentState   state;
    @Autowired private ConfigStore       config;

    @PostMapping("/config")
    public Map<String, Object> configure(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", "");
        if (!id.isBlank()) {
            state.setTournamentId(id.trim());
            config.set("tournamentId", id.trim());
        }

        Object interval = body.get("interval");
        if (interval != null) {
            int secs = Integer.parseInt(interval.toString());
            if (secs > 0) service.startAutoRefresh(secs);
            else          service.stopAutoRefresh();
        }
        return Map.of("ok", true, "tournamentId", state.getTournamentId());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        boolean ok = service.login(body.get("username"), body.get("password"));
        return Map.of("ok", ok, "status", state.getStatus());
    }

    @PostMapping("/fetch/heroes")
    public Map<String, Object> fetchHeroes() {
        service.fetchHeroes();
        return Map.of("ok", true, "status", state.getStatus());
    }

    @PostMapping("/fetch/all")
    public Map<String, Object> fetchAll() {
        service.fetchAll();
        return Map.of("ok", true, "status", state.getStatus());
    }

    @PostMapping("/refresh/start")
    public Map<String, Object> startRefresh(@RequestBody Map<String, Object> body) {
        int secs = Integer.parseInt(body.getOrDefault("interval", "60").toString());
        service.startAutoRefresh(secs);
        return Map.of("ok", true);
    }

    @PostMapping("/refresh/stop")
    public Map<String, Object> stopRefresh() {
        service.stopAutoRefresh();
        return Map.of("ok", true);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "tournamentId", state.getTournamentId(),
            "status",       state.getStatus()
        );
    }

    @GetMapping("/saved-config")
    public Map<String, String> savedConfig() {
        return config.all();
    }
}
