package com.flyfair.search;

import com.flyfair.search.normalize.TextNormalizer;
import com.flyfair.search.rank.FuzzyMatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Focused unit tests for the two pieces of tricky logic: normalization and the fuzzy guards. */
class NormalizerAndFuzzyTest {

    @Test
    void accentsAndCaseFold() {
        assertEquals(TextNormalizer.normalize("Sao Paulo"), TextNormalizer.normalize("São Paulo"));
        assertEquals("munchen", TextNormalizer.normalize("MÜNCHEN"));
        assertEquals("st louis", TextNormalizer.normalize("St. Louis"));
    }

    @Test
    void cjkAndArabicPreservedButCleaned() {
        assertEquals("東京", TextNormalizer.normalize("東京"));
        // Arabic with harakat + tatweel folds to the bare form
        assertEquals(TextNormalizer.normalize("دبي"), TextNormalizer.normalize("دُبَيّ"));
    }

    @Test
    void typoWithinBoundIsMatched() {
        int max = FuzzyMatcher.maxDistanceFor("londn");
        assertTrue(max >= 1);
        assertTrue(FuzzyMatcher.boundedDistance("londn", "london", max) <= max);
    }

    @Test
    void shortQueriesAreNotFuzzed() {
        // "bali" is 4 chars -> below the fuzzy floor, so it can never reach "balikpapan".
        assertEquals(0, FuzzyMatcher.maxDistanceFor("bali"));
    }

    @Test
    void lengthGapPreventsOverreach() {
        // even if fuzzing were enabled, bali->balikpapan is far beyond any allowance
        assertTrue(FuzzyMatcher.boundedDistance("bali", "balikpapan", 2) > 2);
    }
}
