package com.fabestonia.streamlifecounter.tournament;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class TournamentState {

    public static class Pairing {
        public int table;
        public String p1Name, p1Hero, p1Country;
        public String p2Name, p2Hero, p2Country;
        public String result;

        public Pairing(int table,
                       String p1Name, String p1Hero, String p1Country,
                       String p2Name, String p2Hero, String p2Country,
                       String result) {
            this.table = table;
            this.p1Name = p1Name; this.p1Hero = p1Hero; this.p1Country = p1Country;
            this.p2Name = p2Name; this.p2Hero = p2Hero; this.p2Country = p2Country;
            this.result = result;
        }
    }

    public static class Standing {
        public int rank, wins;
        public String name, hero, country;

        public Standing(int rank, String name, String hero, String country, int wins) {
            this.rank = rank; this.name = name; this.hero = hero;
            this.country = country; this.wins = wins;
        }
    }

    // playerName → [hero, country]
    private volatile Map<String, String[]>           heroByPlayer    = new HashMap<>();
    private volatile List<Pairing>                   pairings        = new ArrayList<>();
    private volatile List<Standing>                  standings       = new ArrayList<>();
    private volatile Map<Integer, List<Pairing>>     pairingsByRound = new TreeMap<>();
    private volatile String tournamentId  = "";
    private volatile int    currentRound  = 0;
    private volatile String lastUpdated   = "";
    private volatile String status        = "Not configured";

    public void setTournamentId(String id) { tournamentId = id; }
    public String getTournamentId()        { return tournamentId; }
    public void setStatus(String s)        { status = s; }
    public String getStatus()              { return status; }
    public void setLastUpdated(String s)   { lastUpdated = s; }
    public Map<String, String[]> getHeroByPlayer() { return heroByPlayer; }
    public void setHeroByPlayer(Map<String, String[]> m) { heroByPlayer = m; }
    public void setPairings(List<Pairing> p, int round) { pairings = p; currentRound = round; }
    public void setStandings(List<Standing> s)          { standings = s; }
    public void setPairingsByRound(Map<Integer, List<Pairing>> m) { pairingsByRound = new TreeMap<>(m); }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tournamentId",   tournamentId);
        m.put("round",          currentRound);
        m.put("status",         status);
        m.put("lastUpdated",    lastUpdated);
        m.put("pairings",       pairings);
        m.put("standings",      standings);
        m.put("pairingsByRound", pairingsByRound);
        return m;
    }
}
