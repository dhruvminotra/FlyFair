package com.flyfair.search.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for searchapi.io's {@code google_flights_location_search} engine. We use this ONLY as
 * a reference/benchmark to show our engine's results side-by-side with Google Flights' — not as our
 * search engine. (Outsourcing the ranking/disambiguation/typo/multilingual work would defeat the
 * exercise; this is the "research what the best do and compare" play instead.)
 *
 * <p>The API key is read from the {@code SEARCHAPI_KEY} environment variable (or the
 * {@code searchapi.key} system property). It is deliberately NOT hardcoded in source — a leaked
 * third-party key in a repo is a security smell. When no key is present the comparison is simply
 * disabled and the UI says so.
 */
public final class SearchApiClient {

    private static final String ENDPOINT = "https://www.searchapi.io/api/v1/search";
    private static final String ENGINE = "google_flights_location_search";

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public SearchApiClient() {
        String key = System.getenv("SEARCHAPI_KEY");
        if (key == null || key.isBlank()) key = System.getProperty("searchapi.key");
        this.apiKey = (key == null || key.isBlank()) ? null : key.trim();
    }

    public boolean isEnabled() {
        return apiKey != null;
    }

    /**
     * @return a list of {@code {full_name, type, airports:[{code,title,city,distance}]}} maps as
     *         returned by Google Flights, or a single {@code {error: ...}} map on failure.
     */
    public List<Map<String, Object>> search(String query) {
        if (!isEnabled()) {
            return List.of(Map.of("error", "comparison disabled — set SEARCHAPI_KEY to enable"));
        }
        try {
            String url = ENDPOINT
                    + "?engine=" + ENGINE
                    + "&api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return List.of(Map.of("error", "API returned HTTP " + resp.statusCode()));
            }
            return parse(resp.body());
        } catch (Exception e) {
            return List.of(Map.of("error", "API call failed: " + e.getClass().getSimpleName()));
        }
    }

    private List<Map<String, Object>> parse(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode loc : root.path("locations")) {
            Map<String, Object> city = new LinkedHashMap<>();
            city.put("full_name", loc.path("full_name").asText(""));
            city.put("type", loc.path("type").asText(""));
            List<Map<String, Object>> airports = new ArrayList<>();
            for (JsonNode ap : loc.path("airports")) {
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("code", ap.path("airport_code").asText(""));
                a.put("title", ap.path("title").asText(""));
                a.put("city", ap.path("city").asText(""));
                a.put("distance", ap.path("distance").asText(""));
                airports.add(a);
            }
            city.put("airports", airports);
            out.add(city);
        }
        return out;
    }
}
