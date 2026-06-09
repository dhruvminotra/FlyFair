package com.flyfair.search.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyfair.search.data.DataLoader;
import com.flyfair.search.index.AirportIndex;
import com.flyfair.search.model.Airport;
import com.flyfair.search.model.SearchResult;
import com.flyfair.search.search.AirportSearchService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * A tiny dev server (JDK built-in {@code com.sun.net.httpserver}, no extra dependency) that hosts the
 * search UI. Routes:
 * <ul>
 *   <li>{@code GET /}            — the HTML page</li>
 *   <li>{@code GET /api/search} — the engine's results as JSON</li>
 * </ul>
 *
 * Run: {@code mvn -q compile exec:java -Dexec.mainClass=com.flyfair.search.web.HttpSearchServer}
 */
public final class HttpSearchServer {

    private final AirportSearchService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpSearchServer(AirportSearchService service) {
        this.service = service;
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", System.getProperty("port", "8080")));

        DataLoader loader = new DataLoader();
        AirportIndex index = loader.load();
        HttpSearchServer app = new HttpSearchServer(new AirportSearchService(index));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", app::handleRoot);
        server.createContext("/api/search", app::handleSearch);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.printf("Loaded %,d airports. Search UI at http://localhost:%d/%n", index.size(), port);
    }

    // ---- routes ----

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) { notFound(ex); return; }
        byte[] html;
        try (InputStream in = getClass().getResourceAsStream("/web/index.html")) {
            if (in == null) { send(ex, 500, "text/plain", "index.html not found".getBytes()); return; }
            html = in.readAllBytes();
        }
        send(ex, 200, "text/html; charset=utf-8", html);
    }

    private void handleSearch(HttpExchange ex) throws IOException {
        String q = queryParam(ex, "q");
        List<SearchResult> results = service.search(q, 10);

        List<Map<String, Object>> dto = new ArrayList<>();
        for (SearchResult r : results) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("matchType", r.matchType().name());
            m.put("locationType", r.locationType().name());
            m.put("label", r.label());
            m.put("score", Math.round(r.score() * 100) / 100.0);
            List<Map<String, Object>> airports = new ArrayList<>();
            for (Airport a : r.airports()) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("iata", a.iata());
                am.put("name", a.name());
                am.put("context", a.contextLabel());
                airports.add(am);
            }
            m.put("airports", airports);
            dto.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", q);
        body.put("results", dto);
        sendJson(ex, body);
    }

    // ---- helpers ----

    private void sendJson(HttpExchange ex, Object body) throws IOException {
        send(ex, 200, "application/json; charset=utf-8", mapper.writeValueAsBytes(body));
    }

    private static void notFound(HttpExchange ex) throws IOException {
        send(ex, 404, "text/plain", "not found".getBytes());
    }

    private static String queryParam(HttpExchange ex, String key) {
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return "";
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(status, -1); // headers only, no body
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }
}
