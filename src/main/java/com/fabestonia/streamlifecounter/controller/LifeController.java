package com.fabestonia.streamlifecounter.controller;

import com.fabestonia.streamlifecounter.ConfigStore;
import com.fabestonia.streamlifecounter.GameState;
import com.fabestonia.streamlifecounter.StreamWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

@RestController
public class LifeController {

    private static Timer timer;
    private static int totalTime = 0;
    private static Instant start = Instant.now();
    private static boolean isClockRunning = false;
    private static final int DELAY = 1000;
    private static final int PERIOD = 1000;

    @Autowired private GameState             gameState;
    @Autowired private StreamWebSocketHandler wsHandler;
    @Autowired private ConfigStore            config;

    private void broadcast() {
        wsHandler.broadcast(gameState.toJson());
    }

    @PostMapping("/first/{first}")
    public void updateFirstPlayer(@PathVariable("first") Integer first) {
        gameState.setFirst(first);
        broadcast();
    }

    @PostMapping("/player1/{newLife}")
    public void updatePlayer1Life(@PathVariable("newLife") Integer newLife) {
        int v = Math.max(newLife, 0);
        gameState.setLifeL(v);
        config.set("lifeL", String.valueOf(v));
        broadcast();
    }

    @PostMapping("/hero1/{heroName}")
    public void updatePlayer1Hero(@PathVariable("heroName") String heroName) {
        String v = UriUtils.decode(heroName, StandardCharsets.UTF_8).toUpperCase();
        gameState.setHeroL(v);
        config.set("heroL", v);
        broadcast();
    }

    @PostMapping("/name1/{playerName}")
    public void updatePlayer1Name(@PathVariable("playerName") String playerName) {
        String v = UriUtils.decode(playerName, StandardCharsets.UTF_8).toUpperCase();
        gameState.setPlayerL(v);
        config.set("playerL", v);
        broadcast();
    }

    @PostMapping("/player2/{newLife}")
    public void updatePlayer2Life(@PathVariable("newLife") Integer newLife) {
        int v = Math.max(newLife, 0);
        gameState.setLifeR(v);
        config.set("lifeR", String.valueOf(v));
        broadcast();
    }

    @PostMapping("/hero2/{heroName}")
    public void updatePlayer2Hero(@PathVariable("heroName") String heroName) {
        String v = UriUtils.decode(heroName, StandardCharsets.UTF_8).toUpperCase();
        gameState.setHeroR(v);
        config.set("heroR", v);
        broadcast();
    }

    @PostMapping("/name2/{playerName}")
    public void updatePlayer2Name(@PathVariable("playerName") String playerName) {
        String v = UriUtils.decode(playerName, StandardCharsets.UTF_8).toUpperCase();
        gameState.setPlayerR(v);
        config.set("playerR", v);
        broadcast();
    }

    @PostMapping("/round/{roundNumber}")
    public void updateRound(@PathVariable("roundNumber") String roundNumber) {
        String v = UriUtils.decode(roundNumber, StandardCharsets.UTF_8).toUpperCase();
        gameState.setRound(v);
        config.set("round", v);
        broadcast();
    }

    /**
     * Carrega uma match inteira de uma vez (nomes, heróis, round e vida). Atômico:
     * bumpa o matchVersion exatamente uma vez, para o app da stream reagir só a isto
     * (e não aos ecos das próprias postagens de vida/timer). life1/life2 são opcionais;
     * quando ausentes, a vida atual é preservada.
     */
    @PostMapping("/match/load")
    public Map<String, Object> loadMatch(@RequestBody Map<String, Object> body) {
        String playerL = str(body.get("p1Name")).toUpperCase();
        String playerR = str(body.get("p2Name")).toUpperCase();
        String heroL   = str(body.get("p1Hero")).toUpperCase();
        String heroR   = str(body.get("p2Hero")).toUpperCase();
        String round   = str(body.get("round")).toUpperCase();
        int lifeL = intOr(body.get("life1"), gameState.getLifeL());
        int lifeR = intOr(body.get("life2"), gameState.getLifeR());

        gameState.applyMatchSelection(playerL, heroL, playerR, heroR, round, lifeL, lifeR);

        config.set("playerL", playerL);
        config.set("heroL", heroL);
        config.set("playerR", playerR);
        config.set("heroR", heroR);
        config.set("round", round);
        config.set("lifeL", String.valueOf(lifeL));
        config.set("lifeR", String.valueOf(lifeR));

        broadcast();
        return Map.of("ok", true, "matchVersion", gameState.getMatchVersion());
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static int intOr(Object o, int fallback) {
        if (o == null) return fallback;
        try {
            return Math.max(0, (int) Math.round(Double.parseDouble(o.toString().trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @PostMapping("/trigger/draw")
    public void triggerDraw() {
        gameState.triggerDraw();
        broadcast();
    }

    @PostMapping("/timer/stop")
    public void stopTimer() {
        totalTime = 0;
        isClockRunning = false;
        cancelTimer();
        gameState.setTimer("00:00");
        broadcast();
    }

    @PostMapping("/timer/pause")
    public void pauseTimer() {
        isClockRunning = false;
    }

    @PostMapping("/timer/resume")
    public void resumeTimer() {
        start = Instant.now().plus(totalTime, ChronoUnit.SECONDS);
        isClockRunning = true;
    }

    @PostMapping("/timer/start/blitz")
    public void startBlitzTimer() {
        startTime(2100);
    }

    @PostMapping("/timer/start/cc")
    public void startCCTimer() {
        startTime(3300);
    }

    @PostMapping("/timer/untimed")
    public void untimedTimer() {
        totalTime = 0;
        isClockRunning = false;
        cancelTimer();
        gameState.setTimer("Untimed");
        broadcast();
        startTimeUp();
    }

    @PostMapping("/timer/start/minutes/{minutes}")
    public void startTimerWithMinutes(@PathVariable("minutes") int minutes) {
        startTime(minutes * 60);
    }

    @PostMapping("/timer/start/{seconds}")
    public void startTimerWithSeconds(@PathVariable("seconds") int seconds) {
        startTime(seconds);
    }

    private void cancelTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }

    private void startTime(int time) {
        cancelTimer();
        timer = new Timer();
        start = Instant.now().plus(time, ChronoUnit.SECONDS);
        totalTime = time;
        isClockRunning = true;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isClockRunning) return;
                totalTime--;
                if (totalTime <= 0) {
                    isClockRunning = false;
                    cancelTimer();
                    gameState.setTimer("Time!");
                } else {
                    Duration d = Duration.between(start, Instant.now());
                    int min = d.toMinutesPart() * -1;
                    int sec = d.toSecondsPart() * -1;
                    gameState.setTimer(pad(min) + ":" + pad(sec));
                }
                broadcast();
            }
        }, DELAY, PERIOD);
    }

    private void startTimeUp() {
        cancelTimer();
        timer = new Timer();
        start = Instant.now();
        totalTime = 0;
        isClockRunning = true;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isClockRunning) return;
                totalTime++;
                Duration d = Duration.between(Instant.now(), start);
                int hour = d.toHoursPart() * -1;
                int min = d.toMinutesPart() * -1;
                int sec = d.toSecondsPart() * -1;
                String time = hour > 0
                    ? pad(hour) + ":" + pad(min) + ":" + pad(sec)
                    : pad(min) + ":" + pad(sec);
                gameState.setTimer(time);
                broadcast();
            }
        }, DELAY, PERIOD);
    }

    private String pad(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
