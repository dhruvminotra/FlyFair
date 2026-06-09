package com.flyfair.search.model;

import java.util.List;

/**
 * One ranked search result. May represent a single airport, or a group (a multi-airport
 * city/metro like LON, or a region like Hawaii that expands to several airports).
 */
public final class SearchResult {

    /** How the query matched — also the ranking tier (declaration order = best first). */
    public enum MatchType {
        EXACT_IATA,      // "JFK", "BAH"
        EXACT_ICAO,      // "KJFK"
        EXACT_NAME,      // exact city / airport name
        ALIAS,           // curated friendly/endonym/tourism/metro/multilingual term
        REGION_EXPANSION,// "Hawaii" -> all airports in region
        COUNTRY_EXPANSION,// "Japan" -> the country's major airports
        PREFIX,          // name starts with the query
        FUZZY            // typo-tolerant last resort
    }

    private final LocationType locationType;
    private final MatchType matchType;
    private final String label;          // what to show, e.g. "London (all airports)" or the airport name
    private final List<Airport> airports;// 1 for a single airport, many for a group
    private final double score;
    private final String matchedOn;      // the term that triggered the match (for explainability / demo)

    public SearchResult(LocationType locationType, MatchType matchType, String label,
                        List<Airport> airports, double score, String matchedOn) {
        this.locationType = locationType;
        this.matchType = matchType;
        this.label = label;
        this.airports = airports;
        this.score = score;
        this.matchedOn = matchedOn;
    }

    public LocationType locationType() { return locationType; }
    public MatchType matchType() { return matchType; }
    public String label() { return label; }
    public List<Airport> airports() { return airports; }
    public double score() { return score; }
    public String matchedOn() { return matchedOn; }

    /** Primary airport (first), for single-airport results. */
    public Airport primary() { return airports.isEmpty() ? null : airports.get(0); }

    @Override
    public String toString() {
        String codes = airports.stream().map(Airport::iata).reduce((a, b) -> a + "," + b).orElse("-");
        return String.format("%-7.2f %-16s %-18s %s  [%s]",
                score, matchType, "(" + codes + ")", label, locationType);
    }
}
