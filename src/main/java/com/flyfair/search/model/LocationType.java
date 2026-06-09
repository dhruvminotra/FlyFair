package com.flyfair.search.model;

/**
 * The kind of thing a search result points at. Adapted from the reference platform's
 * {@code LocationType} hierarchy (CONTINENT&rarr;COUNTRY&rarr;STATE&rarr;CITY&rarr;LOCALITY)
 * down to what airport search actually needs.
 *
 * <p>A query can resolve to a single airport, a whole city/metro (multiple airports), or a
 * region/country (expand to all airports within). The type drives how the result is rendered
 * and grouped.
 */
public enum LocationType {
    /** A single physical airport, e.g. JFK. */
    AIRPORT,
    /** A city that maps to one or more airports, e.g. Brussels &rarr; BRU. */
    CITY,
    /** A multi-airport metro served by an IATA metropolitan code, e.g. LON &rarr; LHR/LGW/STN/LCY/LTN. */
    MULTI_AIRPORT_CITY,
    /** A state/province/region; expands to every airport whose iso_region matches, e.g. Hawaii. */
    REGION,
    /** A country; expands to its airports. */
    COUNTRY
}
