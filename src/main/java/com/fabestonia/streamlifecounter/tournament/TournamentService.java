package com.fabestonia.streamlifecounter.tournament;

import com.fabestonia.streamlifecounter.ConfigStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.regex.*;

@Service
public class TournamentService {

    @Autowired private TournamentState          state;
    @Autowired private TournamentWebSocketHandler wsHandler;
    @Autowired private ConfigStore               config;

    private static final String BASE_URL = "https://gem.fabtcg.com";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> refreshTask;

    // ── Auth ─────────────────────────────────────────────────────────────────

    public boolean login(String username, String password) {
        try {
            state.setStatus("Logging in…");
            broadcast();

            // 1. GET root page → extract csrfmiddlewaretoken from hidden form field
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/"))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String csrf = csrfFromHtml(getResp.body());
            if (csrf == null) csrf = csrfFromCookies();
            if (csrf == null) {
                state.setStatus("Login failed: CSRF token not found");
                broadcast();
                return false;
            }

            // 2. POST to root — form action is "/"
            String body = "username=" + enc(username)
                    + "&password=" + enc(password)
                    + "&csrfmiddlewaretoken=" + enc(csrf);

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", BASE_URL + "/")
                    .header("User-Agent", "Mozilla/5.0")
                    .build();
            HttpResponse<String> postResp = httpClient.send(postReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            // Success: redirected away from root (to /profile/... or similar)
            boolean ok = cookieManager.getCookieStore().getCookies()
                    .stream().anyMatch(c -> c.getName().equals("sessionid"))
                    || !postResp.uri().getPath().equals("/");

            state.setStatus(ok ? "Logged in ✓" : "Login failed: check credentials");
            if (ok) {
                config.set("username", username);
                config.set("password", password);
            }
            broadcast();
            return ok;

        } catch (Exception e) {
            state.setStatus("Login error: " + e.getMessage());
            broadcast();
            return false;
        }
    }

    private String csrfFromCookies() {
        return cookieManager.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals("csrftoken"))
                .map(HttpCookie::getValue)
                .findFirst().orElse(null);
    }

    private String csrfFromHtml(String html) {
        // name="csrfmiddlewaretoken" ... value="TOKEN"
        Matcher m = Pattern.compile("name=['\"]csrfmiddlewaretoken['\"][^>]*value=['\"]([^'\"]+)['\"]").matcher(html);
        if (m.find()) return m.group(1);
        // value="TOKEN" ... name="csrfmiddlewaretoken"
        m = Pattern.compile("value=['\"]([^'\"]{20,})['\"][^>]*name=['\"]csrfmiddlewaretoken['\"]").matcher(html);
        if (m.find()) return m.group(1);
        return null;
    }

    // ── Fetch ────────────────────────────────────────────────────────────────

    public void fetchHeroes() {
        String id = state.getTournamentId();
        if (id.isEmpty()) { state.setStatus("Set a tournament ID first"); broadcast(); return; }
        try {
            String csv = get("/gem/" + id + "/coverage/heroes");
            state.setHeroByPlayer(parseHeroesCsv(csv));
            state.setStatus("Heroes loaded (" + state.getHeroByPlayer().size() + " players)");
            broadcast();
        } catch (Exception e) {
            state.setStatus("Heroes error: " + e.getMessage());
            broadcast();
        }
    }

    public void fetchPairings() {
        String id = state.getTournamentId();
        if (id.isEmpty()) return;
        try {
            String csv = get("/gem/" + id + "/coverage/pairings");
            parsePairings(csv);
            state.setLastUpdated(LocalTime.now().format(TIME_FMT));
            broadcast();
        } catch (Exception e) {
            state.setStatus("Pairings error: " + e.getMessage());
            broadcast();
        }
    }

    public void fetchStandings() {
        String id = state.getTournamentId();
        if (id.isEmpty()) return;
        try {
            // The overall standings (/coverage/standings) stay empty while the
            // tournament is in progress; each finished round has its own at
            // /coverage/standings/{runId}. Fall back to the most recent round.
            List<TournamentState.Standing> standings = parseStandings(get("/gem/" + id + "/coverage/standings"));
            if (standings.isEmpty()) {
                for (String path : roundStandingsPaths(id)) {
                    standings = parseStandings(get(path));
                    if (!standings.isEmpty()) break;
                }
            }
            state.setStandings(standings);
            broadcast();
        } catch (Exception e) {
            state.setStatus("Standings error: " + e.getMessage());
            broadcast();
        }
    }

    /**
     * Per-round standings paths, most recent first. The /run/ HTML page links to
     * each finished round's standings (/coverage/standings/{runId}); rounds with
     * no standings yet produce no link, so they are skipped.
     */
    private List<String> roundStandingsPaths(String id) {
        List<String> paths = new ArrayList<>();
        try {
            String html = get("/gem/" + id + "/run/");
            Matcher m = Pattern.compile("/gem/" + Pattern.quote(id) + "/coverage/standings/(\\d+)").matcher(html);
            LinkedHashSet<String> runIds = new LinkedHashSet<>();
            while (m.find()) runIds.add(m.group(1));
            for (String runId : runIds) {
                paths.add("/gem/" + id + "/coverage/standings/" + runId);
            }
        } catch (Exception ignored) {}
        return paths;
    }

    public void fetchAll() {
        fetchPairings();
        fetchStandings();
    }

    // ── Auto-refresh ─────────────────────────────────────────────────────────

    public void startAutoRefresh(int seconds) {
        stopAutoRefresh();
        refreshTask = scheduler.scheduleAtFixedRate(this::fetchAll, 0, seconds, TimeUnit.SECONDS);
    }

    public void stopAutoRefresh() {
        if (refreshTask != null) { refreshTask.cancel(false); refreshTask = null; }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "text/csv,text/plain,*/*")
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
        return resp.body();
    }

    // ── CSV parsing ───────────────────────────────────────────────────────────

    private Map<String, String[]> parseHeroesCsv(String csv) {
        Map<String, String[]> map = new LinkedHashMap<>();
        List<List<String>> rows = parseCsv(csv);
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size() < 4) continue;
            map.put(r.get(0).trim(), new String[]{ cleanHeroName(r.get(3)), r.get(2).trim() });
        }
        return map;
    }

    private void parsePairings(String csv) {
        List<List<String>> rows = parseCsv(csv);
        if (rows.size() <= 1) return;

        Map<String, String[]> heroes = state.getHeroByPlayer();
        Map<Integer, List<TournamentState.Pairing>> byRound = new TreeMap<>();

        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size() < 6) continue;
            try {
                int round  = Integer.parseInt(r.get(0).trim());
                int table  = Integer.parseInt(r.get(1).trim());
                String p1  = r.get(2).trim();
                String p2  = r.get(4).trim();
                String res = r.size() > 6 ? r.get(6).trim() : "";
                String[] h1 = heroOf(heroes, p1);
                String[] h2 = heroOf(heroes, p2);
                byRound.computeIfAbsent(round, k -> new ArrayList<>())
                       .add(new TournamentState.Pairing(table, p1, h1[0], h1[1], p2, h2[0], h2[1], res));
            } catch (Exception ignored) {}
        }

        byRound.values().forEach(list -> list.sort(Comparator.comparingInt(p -> p.table)));

        int maxRound = byRound.isEmpty() ? 0 : Collections.max(byRound.keySet());
        state.setPairingsByRound(byRound);
        state.setPairings(byRound.getOrDefault(maxRound, new ArrayList<>()), maxRound);
    }

    private List<TournamentState.Standing> parseStandings(String csv) {
        List<List<String>> rows = parseCsv(csv);
        Map<String, String[]> heroes = state.getHeroByPlayer();
        List<TournamentState.Standing> list = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (r.size() < 4) continue;
            try {
                int rank = Integer.parseInt(r.get(0).trim());
                String name = r.get(1).trim();
                int wins = Integer.parseInt(r.get(3).trim());
                String[] h = heroOf(heroes, name);
                list.add(new TournamentState.Standing(rank, name, h[0], h[1], wins));
            } catch (Exception ignored) {}
        }
        return list;
    }

    private String[] heroOf(Map<String, String[]> heroes, String name) {
        String[] h = heroes.get(name);
        if (h != null) return h;
        // Case-insensitive fallback
        for (Map.Entry<String, String[]> e : heroes.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return new String[]{ "", "" };
    }

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : content.split("\r?\n")) {
            if (line.trim().isEmpty()) continue;
            rows.add(parseLine(line));
        }
        return rows;
    }

    private List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQ = false;
        for (char c : line.toCharArray()) {
            if (c == '"')      { inQ = !inQ; }
            else if (c == ',' && !inQ) { fields.add(field.toString()); field = new StringBuilder(); }
            else               { field.append(c); }
        }
        fields.add(field.toString());
        return fields;
    }

    private static String cleanHeroName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s*\\([^)]*\\)\\s*$", "");
    }

    private String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private void broadcast() {
        try { wsHandler.broadcast(mapper.writeValueAsString(state.toMap())); } catch (Exception ignored) {}
    }
}
