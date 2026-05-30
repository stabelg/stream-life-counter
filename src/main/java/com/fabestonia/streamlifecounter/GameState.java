package com.fabestonia.streamlifecounter;

import org.springframework.stereotype.Component;

@Component
public class GameState {

    private volatile int lifeL = 20;
    private volatile int lifeR = 20;
    private volatile String heroL = "";
    private volatile String heroR = "";
    private volatile String playerL = "PLAYER1";
    private volatile String playerR = "PLAYER2";
    private volatile String round = "ROUND 1";
    private volatile String timer = "";
    private volatile int drawTrigger = 0;
    private volatile int first = 0;

    public void setLifeL(int v) { lifeL = v; }
    public void setLifeR(int v) { lifeR = v; }
    public void setHeroL(String v) { heroL = v; }
    public void setHeroR(String v) { heroR = v; }
    public void setPlayerL(String v) { playerL = v; }
    public void setPlayerR(String v) { playerR = v; }
    public void setRound(String v) { round = v; }
    public void setTimer(String v) { timer = v; }
    public void setFirst(int v) { first = v; }
    public void triggerDraw() { drawTrigger++; }

    public String toJson() {
        return String.format(
            "{\"lifeL\":%d,\"lifeR\":%d,\"heroL\":\"%s\",\"heroR\":\"%s\"," +
            "\"playerL\":\"%s\",\"playerR\":\"%s\",\"round\":\"%s\"," +
            "\"timer\":\"%s\",\"drawTrigger\":%d,\"first\":%d}",
            lifeL, lifeR, esc(heroL), esc(heroR),
            esc(playerL), esc(playerR), esc(round),
            esc(timer), drawTrigger, first
        );
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
