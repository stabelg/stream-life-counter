package com.fabestonia.streamlifecounter.controller;

import com.fabestonia.streamlifecounter.ConfigStore;
import com.fabestonia.streamlifecounter.GameState;
import com.fabestonia.streamlifecounter.StreamWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        String v = playerName.toUpperCase();
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
        String v = playerName.toUpperCase();
        gameState.setPlayerR(v);
        config.set("playerR", v);
        broadcast();
    }

    @PostMapping("/round/{roundNumber}")
    public void updateRound(@PathVariable("roundNumber") String roundNumber) {
        String v = roundNumber.toUpperCase();
        gameState.setRound(v);
        config.set("round", v);
        broadcast();
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
