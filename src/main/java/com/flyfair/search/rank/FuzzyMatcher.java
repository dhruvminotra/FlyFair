package com.flyfair.search.rank;

/**
 * Typo tolerance via bounded Damerau-Levenshtein (optimal string alignment) edit distance,
 * with deliberate <em>overreach guards</em>. Naive fuzzy search is where things break
 * ("Bali"&rarr;"Balikpapan", "Florida"&rarr;"La Florida"); the guards keep it honest:
 *
 * <ul>
 *   <li>No fuzzing of short queries (&lt; {@value #MIN_FUZZY_LEN} chars) — too little signal.</li>
 *   <li>Allowance scales with length: 1 edit for medium terms, 2 only for long ones.</li>
 *   <li>A length-gap pre-check rejects pairs that cannot possibly be within the allowance
 *       (so "bali" can never reach "balikpapan").</li>
 * </ul>
 *
 * Fuzzy is also only ever consulted when exact/alias/region tiers found nothing — enforced by
 * the search service, not here.
 */
public final class FuzzyMatcher {

    private static final int MIN_FUZZY_LEN = 5;

    private FuzzyMatcher() {}

    /** Largest edit distance we will tolerate for a query of this (normalized) length. */
    public static int maxDistanceFor(String query) {
        int len = query.length();
        if (len < MIN_FUZZY_LEN) return 0;   // 0 => fuzzy disabled
        return len >= 8 ? 2 : 1;
    }

    /**
     * @return the edit distance if it is within {@code max}, otherwise {@code max + 1}
     *         (i.e. "too far"). Returns {@code max + 1} immediately when lengths differ by
     *         more than {@code max}.
     */
    public static int boundedDistance(String a, String b, int max) {
        if (max <= 0) return max + 1;
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        if (a.equals(b)) return 0;

        int n = a.length(), m = b.length();
        int[] prevPrev = new int[m + 1];
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char ai = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char bj = b.charAt(j - 1);
                int cost = (ai == bj) ? 0 : 1;
                int val = Math.min(Math.min(
                        prev[j] + 1,        // deletion
                        curr[j - 1] + 1),   // insertion
                        prev[j - 1] + cost);// substitution
                if (i > 1 && j > 1 && ai == b.charAt(j - 2) && a.charAt(i - 2) == bj) {
                    val = Math.min(val, prevPrev[j - 2] + 1); // transposition
                }
                curr[j] = val;
                rowMin = Math.min(rowMin, val);
            }
            if (rowMin > max) return max + 1; // whole row exceeded the bound: prune early
            int[] tmp = prevPrev; prevPrev = prev; prev = curr; curr = tmp;
        }
        return prev[m] <= max ? prev[m] : max + 1;
    }
}
