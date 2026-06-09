package com.flyfair.search.index;

import com.flyfair.search.model.Airport;
import com.flyfair.search.normalize.TextNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory search index. Holds several purpose-built maps so the ranker can ask precise
 * questions (exact IATA? exact name? region? curated alias?) rather than fuzzing one big field.
 * Modelled on the reference platform's {@code LocationManager}: categorized lookups plus an
 * "equivalent term" notion (here, curated aliases &amp; multilingual labels).
 *
 * <p>Maps point straight at {@link Airport}; the match <em>tier</em> (which map answered) decides
 * the score, so there is no per-entry weight object to maintain.
 */
public final class AirportIndex {

    private final List<Airport> all = new ArrayList<>();

    private final Map<String, Airport> byIata = new HashMap<>();          // "JFK" -> airport
    private final Map<String, Airport> byIcao = new HashMap<>();          // norm("KJFK") -> airport
    private final Map<String, List<Airport>> byName = new HashMap<>();    // norm(airport/city name) -> airports
    private final Map<String, List<Airport>> byAlias = new HashMap<>();   // norm(curated alias/multilingual) -> airports
    private final Map<String, List<Airport>> byRegion = new HashMap<>();  // norm(region name) -> airports
    private final Map<String, List<Airport>> byCountry = new HashMap<>(); // norm(country name) -> airports

    /** Union of all searchable phrases (names + cities + aliases) for prefix / fuzzy scanning. */
    private final Map<String, List<Airport>> byTerm = new HashMap<>();

    // ---- build-time population (called by DataLoader) ----

    public void addAirport(Airport a) {
        all.add(a);
        byIata.put(a.iata().toUpperCase(), a);
        if (a.icao() != null && !a.icao().isBlank()) {
            byIcao.put(TextNormalizer.normalize(a.icao()), a);
        }
        indexName(a.name(), a);
        if (a.city() != null && !a.city().isBlank()) {
            indexName(a.city(), a);
        }
        if (a.regionName() != null && !a.regionName().isBlank()) {
            add(byRegion, TextNormalizer.normalize(a.regionName()), a);
        }
        if (a.country() != null && !a.country().isBlank()) {
            add(byCountry, TextNormalizer.normalize(a.country()), a);
        }
    }

    private void indexName(String raw, Airport a) {
        String key = TextNormalizer.normalize(raw);
        if (key.isEmpty()) return;
        add(byName, key, a);
        add(byTerm, key, a);
    }

    /** Register a curated alias / multilingual label that should resolve to {@code a}. */
    public void addAlias(String aliasTerm, Airport a) {
        String key = TextNormalizer.normalize(aliasTerm);
        if (key.isEmpty()) return;
        add(byAlias, key, a);
        add(byTerm, key, a);
        a.addAltName(aliasTerm);
    }

    private static void add(Map<String, List<Airport>> map, String key, Airport a) {
        List<Airport> list = map.computeIfAbsent(key, k -> new ArrayList<>());
        if (!list.contains(a)) list.add(a);
    }

    // ---- query helpers (used by the ranker) ----

    public Airport byIata(String upperIata) { return byIata.get(upperIata); }
    public Airport byIcao(String normIcao) { return byIcao.get(normIcao); }
    public List<Airport> byExactName(String norm) { return byName.getOrDefault(norm, List.of()); }
    public List<Airport> byAlias(String norm) { return byAlias.getOrDefault(norm, List.of()); }
    public List<Airport> byRegion(String norm) { return byRegion.getOrDefault(norm, List.of()); }
    public List<Airport> byCountry(String norm) { return byCountry.getOrDefault(norm, List.of()); }
    public List<Airport> byTerm(String norm) { return byTerm.getOrDefault(norm, List.of()); }

    /** Distinct searchable phrases, for prefix / fuzzy candidate generation. */
    public Set<String> allTerms() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(byTerm.keySet()));
    }

    public Collection<Airport> all() { return Collections.unmodifiableList(all); }
    public int size() { return all.size(); }
}
