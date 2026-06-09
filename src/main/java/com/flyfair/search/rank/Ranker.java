package com.flyfair.search.rank;

import com.flyfair.search.model.SearchResult.MatchType;

/**
 * Turns a (match tier, importance, fuzzy distance) into a comparable score. The tier dominates —
 * an exact IATA always outranks a fuzzy hit — and airport {@code importance} only breaks ties
 * within a tier (so London-UK beats London-Ontario, JFK-the-airport beats a same-named hamlet).
 */
public final class Ranker {

    private Ranker() {}

    /** Tier base: earlier MatchType constants score higher, spaced far enough that tiers never overlap. */
    private static double tierBase(MatchType type) {
        return 1000.0 - type.ordinal() * 100.0;
    }

    public static double score(MatchType type, int importance, int fuzzyDistance) {
        // importance (0..~35) contributes <1 point so it can only break ties, never cross tiers.
        return tierBase(type) + importance * 0.01 - fuzzyDistance * 25.0;
    }
}
