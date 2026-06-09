package com.flyfair.search.model;

/**
 * Provenance of a record / index entry. Mirrors the {@code dataSourceType} idea from the
 * reference platform's GeoInformation entity, so the memo can speak to build-vs-fake honestly.
 */
public enum DataSource {
    /** Sourced from the pruned OurAirports dataset (the spine). */
    OURAIRPORTS,
    /** Hand-curated alias / multilingual mapping that doesn't exist cleanly in the raw data. */
    CURATED
}
