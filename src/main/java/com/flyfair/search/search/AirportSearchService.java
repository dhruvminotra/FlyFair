package com.flyfair.search.search;

import com.flyfair.search.index.AirportIndex;
import com.flyfair.search.model.Airport;
import com.flyfair.search.model.LocationType;
import com.flyfair.search.model.SearchResult;
import com.flyfair.search.model.SearchResult.MatchType;
import com.flyfair.search.normalize.TextNormalizer;
import com.flyfair.search.rank.FuzzyMatcher;
import com.flyfair.search.rank.Ranker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The public search entry point. Generates candidates from the index in tier order, groups
 * airports into city / metro / region results, ranks them, and only falls back to prefix and
 * (guarded) fuzzy matching when no exact-quality match was found.
 *
 * <p>The "only fuzz when nothing exact matched" rule is the single most important guard against
 * overreach (Florida&rarr;La Florida, Bali&rarr;Balikpapan): if a real answer exists we never
 * dilute it with approximate ones.
 */
public final class AirportSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_FALLBACK_GROUPS = 25; // cap prefix/fuzzy fan-out
    private static final int MAX_COUNTRY_AIRPORTS = 15; // a country result shows its major airports only

    private final AirportIndex index;

    public AirportSearchService(AirportIndex index) {
        this.index = index;
    }

    public List<SearchResult> search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        String raw = query.trim();
        String norm = TextNormalizer.normalize(raw);
        if (norm.isEmpty()) return List.of();

        List<SearchResult> results = new ArrayList<>();
        Set<String> covered = new HashSet<>(); // IATA codes already represented by a result

        // Tier 1: exact IATA (3 letters) — solves the code->airport direction (TUL, CTA, BAH).
        if (raw.length() == 3 && isAlpha(raw)) {
            Airport a = index.byIata(raw.toUpperCase());
            if (a != null) addGroup(results, covered, List.of(a), MatchType.EXACT_IATA, raw, null, 0);
        }
        // Tier 2: exact ICAO (4 letters).
        if (norm.length() == 4) {
            Airport a = index.byIcao(norm);
            if (a != null) addGroup(results, covered, List.of(a), MatchType.EXACT_ICAO, raw, null, 0);
        }
        // Tier 3: curated alias / multilingual / metro. Defines one explicit set => one group.
        // Runs before name matching so an intentional grouping (Tokyo -> HND+NRT) wins over a
        // partial city hit, and the partial hit is then suppressed by coverage.
        List<Airport> aliasHits = index.byAlias(norm);
        if (!aliasHits.isEmpty()) {
            addGroup(results, covered, dedupeAirports(aliasHits), MatchType.ALIAS, raw, null, 0);
        }
        // Tier 4: exact name / city. Group by distinct city-location -> natural disambiguation.
        for (List<Airport> group : groupByCity(index.byExactName(norm))) {
            addGroup(results, covered, group, MatchType.EXACT_NAME, raw, null, 0);
        }
        // Tier 5: region / state expansion (Hawaii, Ontario).
        List<Airport> regionHits = index.byRegion(norm);
        if (!regionHits.isEmpty()) {
            addGroup(results, covered, dedupeAirports(regionHits), MatchType.REGION_EXPANSION, raw,
                    LocationType.REGION, 0);
        }
        // Tier 6: country expansion (Japan). Capped to the major airports — we have no passenger
        // -volume signal to rank a country's airports finely, so we surface the largest ones.
        List<Airport> countryHits = index.byCountry(norm);
        if (!countryHits.isEmpty()) {
            List<Airport> major = sortByImportance(dedupeAirports(countryHits));
            if (major.size() > MAX_COUNTRY_AIRPORTS) major = major.subList(0, MAX_COUNTRY_AIRPORTS);
            addGroup(results, covered, major, MatchType.COUNTRY_EXPANSION, raw, LocationType.COUNTRY, 0);
        }

        // Fallbacks: only if nothing exact-quality matched.
        if (results.isEmpty()) {
            addPrefixMatches(results, covered, norm);
        }
        if (results.isEmpty()) {
            addFuzzyMatches(results, covered, norm);
        }

        results.sort(Comparator.comparingDouble(SearchResult::score).reversed()
                .thenComparing(SearchResult::label));
        return results.size() > limit ? new ArrayList<>(results.subList(0, limit)) : results;
    }

    // ---- candidate builders ----

    private void addPrefixMatches(List<SearchResult> results, Set<String> covered, String norm) {
        if (norm.length() < 2) return;
        List<Airport> hits = new ArrayList<>();
        for (String term : index.allTerms()) {
            if (term.startsWith(norm)) hits.addAll(index.byTerm(term));
        }
        int added = 0;
        for (List<Airport> group : groupByCity(dedupeAirports(hits))) {
            if (added++ >= MAX_FALLBACK_GROUPS) break;
            addGroup(results, covered, group, MatchType.PREFIX, norm, null, 0);
        }
    }

    private void addFuzzyMatches(List<SearchResult> results, Set<String> covered, String norm) {
        int max = FuzzyMatcher.maxDistanceFor(norm);
        if (max <= 0) return;
        Map<Airport, Integer> bestByAirport = new LinkedHashMap<>(); // airport -> smallest distance
        for (String term : index.allTerms()) {
            int d = FuzzyMatcher.boundedDistance(norm, term, max);
            if (d <= max) {
                for (Airport a : index.byTerm(term)) bestByAirport.merge(a, d, Math::min);
            }
        }
        if (bestByAirport.isEmpty()) return;
        int added = 0;
        for (List<Airport> group : groupByCity(new ArrayList<>(bestByAirport.keySet()))) {
            if (added++ >= MAX_FALLBACK_GROUPS) break;
            int dist = group.stream().mapToInt(a -> bestByAirport.getOrDefault(a, max)).min().orElse(max);
            addGroup(results, covered, group, MatchType.FUZZY, norm, null, dist);
        }
    }

    // ---- result assembly ----

    /**
     * Build and add a result, unless every airport in it is already represented by a
     * higher-tier result (coverage suppression). This both de-dupes identical sets and stops
     * a partial city hit from echoing a curated grouping (e.g. HND alone after Tokyo->HND+NRT).
     */
    private void addGroup(List<SearchResult> results, Set<String> covered, List<Airport> airports,
                          MatchType tier, String matchedOn, LocationType forcedType, int fuzzyDistance) {
        if (airports.isEmpty()) return;
        List<Airport> sorted = sortByImportance(airports);
        if (sorted.stream().allMatch(a -> covered.contains(a.iata()))) return;
        LocationType type = forcedType != null ? forcedType
                : (sorted.size() > 1 ? LocationType.MULTI_AIRPORT_CITY : LocationType.AIRPORT);
        int maxImp = sorted.stream().mapToInt(Airport::importance).max().orElse(0);
        double score = Ranker.score(tier, maxImp, fuzzyDistance);
        results.add(new SearchResult(type, tier, label(sorted, type), sorted, score, matchedOn));
        for (Airport a : sorted) covered.add(a.iata());
    }

    // ---- helpers ----

    /** Group airports by distinct (city, region, country) so same-named cities split out. */
    private List<List<Airport>> groupByCity(List<Airport> airports) {
        Map<String, List<Airport>> groups = new LinkedHashMap<>();
        for (Airport a : airports) {
            String city = TextNormalizer.normalize(a.city() == null ? a.name() : a.city());
            String key = city + "|" + a.isoRegion() + "|" + a.isoCountry();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }
        return new ArrayList<>(groups.values());
    }

    private static List<Airport> sortByImportance(List<Airport> airports) {
        List<Airport> copy = new ArrayList<>(airports);
        copy.sort(Comparator.comparingInt(Airport::importance).reversed()
                .thenComparing(Airport::iata));
        return copy;
    }

    private static List<Airport> dedupeAirports(List<Airport> airports) {
        List<Airport> out = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (Airport a : airports) if (codes.add(a.iata())) out.add(a);
        return out;
    }

    private static String label(List<Airport> airports, LocationType type) {
        Airport first = airports.get(0);
        if (type == LocationType.REGION) {
            return first.regionName() + " (" + airports.size() + " airports in region)";
        }
        if (type == LocationType.COUNTRY) {
            return first.country() + " — major airports (showing " + airports.size() + ")";
        }
        if (type == LocationType.MULTI_AIRPORT_CITY) {
            String city = first.city() != null && !first.city().isBlank() ? first.city() : first.name();
            return city + " — all airports (" + first.country() + ")";
        }
        return first.name() + " (" + first.contextLabel() + ")";
    }

    private static boolean isAlpha(String s) {
        for (int i = 0; i < s.length(); i++) if (!Character.isLetter(s.charAt(i))) return false;
        return true;
    }
}
