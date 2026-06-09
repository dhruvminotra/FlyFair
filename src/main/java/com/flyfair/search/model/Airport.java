package com.flyfair.search.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single commercial airport, the atomic searchable unit. Populated from the pruned
 * OurAirports snapshot; alternate names (endonyms, multilingual, aliases) are attached later
 * by the loader from the curated files.
 */
public final class Airport {

    private final String iata;        // 3-letter IATA, e.g. "JFK" (always present after pruning)
    private final String icao;        // 4-letter ICAO / ident, e.g. "KJFK"
    private final String type;        // large_airport | medium_airport | small_airport
    private final String name;        // "John F Kennedy International Airport"
    private final String city;        // municipality, e.g. "New York"
    private final String isoRegion;   // e.g. "US-NY"
    private final String regionName;  // e.g. "New York" (resolved from regions.csv at prune time)
    private final String isoCountry;  // e.g. "US"
    private final String country;     // "United States" (resolved at prune time)
    private final double lat;
    private final double lon;

    /**
     * Ranking prior: bigger = more important / more likely what the user meant when names collide.
     * Derived from airport type (large &gt; medium &gt; small). Used only to break ties between
     * same-tier matches (e.g. London UK vs London, Ontario).
     */
    private final int importance;

    /** Extra names that should also match this airport: city, aliases, multilingual labels. */
    private final List<String> altNames = new ArrayList<>();

    public Airport(String iata, String icao, String type, String name, String city,
                   String isoRegion, String regionName, String isoCountry, String country,
                   double lat, double lon, int importance) {
        this.iata = iata;
        this.icao = icao;
        this.type = type;
        this.name = name;
        this.city = city;
        this.isoRegion = isoRegion;
        this.regionName = regionName;
        this.isoCountry = isoCountry;
        this.country = country;
        this.lat = lat;
        this.lon = lon;
        this.importance = importance;
    }

    public String iata() { return iata; }
    public String icao() { return icao; }
    public String type() { return type; }
    public String name() { return name; }
    public String city() { return city; }
    public String isoRegion() { return isoRegion; }
    public String regionName() { return regionName; }
    public String isoCountry() { return isoCountry; }
    public String country() { return country; }
    public double lat() { return lat; }
    public double lon() { return lon; }
    public int importance() { return importance; }
    public List<String> altNames() { return altNames; }

    public void addAltName(String alt) {
        if (alt != null && !alt.isBlank()) altNames.add(alt);
    }

    /** Human-readable context for disambiguation, e.g. "New York, United States". */
    public String contextLabel() {
        StringBuilder sb = new StringBuilder();
        if (city != null && !city.isBlank()) sb.append(city);
        if (regionName != null && !regionName.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(regionName);
        }
        if (country != null && !country.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(country);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return iata + " — " + name + " (" + contextLabel() + ")";
    }
}
