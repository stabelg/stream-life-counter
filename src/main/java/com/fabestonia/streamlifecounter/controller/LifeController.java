package com.fabestonia.streamlifecounter.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
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

    @Value("${life.counter.path}")
    private String defaultPath;

    @PostMapping("/player1/{newLife}")
    public void updatePlayer1Life(@PathVariable("newLife") Integer newLife) {
        writeFile("LifeL.txt", lifeAsString(newLife));
    }

    @PostMapping("/hero1/{heroName}")
    public void updatePlayer1Hero(@PathVariable("heroName") String heroName) {
        writeFile("HeroL.txt", toUpperCase(UriUtils.decode(heroName, StandardCharsets.UTF_8)));
    }

    @PostMapping("/name1/{playerName}")
    public void updatePlayer1Name(@PathVariable("playerName") String playerName) {
        writeFile("PlayerL.txt", toUpperCase(playerName));
    }

    @PostMapping("/player2/{newLife}")
    public void updatePlayer2Life(@PathVariable("newLife") Integer newLife) {
        writeFile("LifeR.txt", lifeAsString(newLife));
    }

    @PostMapping("/hero2/{heroName}")
    public void updatePlayer2Hero(@PathVariable("heroName") String heroName) {
        writeFile("HeroR.txt", toUpperCase(UriUtils.decode(heroName, StandardCharsets.UTF_8)));
    }

    @PostMapping("/name2/{playerName}")
    public void updatePlayer2Name(@PathVariable("playerName") String playerName) {
        writeFile("PlayerR.txt", toUpperCase(playerName));
    }

    @PostMapping("/round/{roundNumber}")
    public void updateRound(@PathVariable("roundNumber") String roundNumber) {
        writeFile("Round.txt", toUpperCase(roundNumber));
    }

    @PostMapping("/timer/stop")
    public void stopTimer() {
        totalTime = 0;
        isClockRunning = false;
        if(timer != null) {
            timer.cancel();
            timer.purge();
        }
        writeFile("Timer.txt", "00:00");
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

    @PostMapping("/timer/start/{minutes}")
    public void startTimerWithMinutes(@PathVariable("minutes") int minutes) {
        startTime(minutes*60);
    }

    private String toUpperCase(String text){
        return text.toUpperCase();
    }

    private String lifeAsString(Integer newLife){
        String life = ""+newLife;
        if(newLife <= 0){
            life = "0";
        }
        return life;
    }

    private void startTime(int time){
        if(timer != null) {
            timer.cancel();
            timer.purge();
        }
        timer = new Timer();
        start = Instant.now().plus(time, ChronoUnit.SECONDS);
        totalTime = time;
        isClockRunning = true;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(isClockRunning) {
                    totalTime--;
                    if(totalTime == 0){
                        isClockRunning = false;
                        timer.cancel();
                        timer.purge();
                        writeFile("Timer.txt", "Time!");
                    } else {
                        Duration d = Duration.between(start, Instant.now());
                        int minute = d.toMinutesPart() * -1;
                        String minutes =  minute < 10 ? "0"+minute : minute+"";
                        int second = d.toSecondsPart() * -1;
                        String seconds =  second < 10 ? "0"+second : second+"";
                        writeFile("Timer.txt", (minutes + ":" + seconds));
                    }
                }
            }
        }, DELAY, PERIOD);
    }

    private void writeFile(String fileName, String value){
        try {
            File file = new File(defaultPath+fileName);
            Path from = file.toPath(); //convert from File to Path
            Path to = Paths.get(defaultPath+fileName);
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            FileWriter myWriter = null;

            myWriter = new FileWriter(file.getAbsolutePath(), false);
            myWriter.write(value);
            myWriter.close();
            System.out.println("Successfully wrote to the file: " + fileName + ". with: " +value);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            throw new RuntimeException(e);
        }

    }
}
