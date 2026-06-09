package com.flyfair.search.harness;

import com.flyfair.search.data.DataLoader;
import com.flyfair.search.index.AirportIndex;
import com.flyfair.search.model.SearchResult;
import com.flyfair.search.search.AirportSearchService;

import java.util.List;
import java.util.Set;

/**
 * CLI entry point — the "UI" for this prototype.
 *
 * <pre>
 *   mvn exec:java                                  # run the full golden-set scorecard
 *   mvn exec:java -Dexec.args="Hawaii"             # run a single query
 *   mvn exec:java -Dexec.args="--eval"             # explicit eval mode
 * </pre>
 */
public final class SearchHarness {

    public static void main(String[] args) {
        DataLoader loader = new DataLoader();
        AirportIndex index = loader.load();
        AirportSearchService service = new AirportSearchService(index);

        System.out.printf("Loaded %,d commercial airports.%n", index.size());
        if (!loader.rejectedCodes().isEmpty()) {
            System.out.println("Rejected curated codes (validation caught these):");
            loader.rejectedCodes().forEach(r -> System.out.println("  - " + r));
        }

        if (args.length == 0 || args[0].equals("--eval")) {
            runEval(service, index);
        } else {
            runQuery(service, String.join(" ", args));
        }
    }

    private static void runQuery(AirportSearchService service, String query) {
        System.out.println("\nQuery: \"" + query + "\"");
        List<SearchResult> results = service.search(query);
        if (results.isEmpty()) {
            System.out.println("  (no results)");
            return;
        }
        for (SearchResult r : results) System.out.println("  " + r);
    }

    private static void runEval(AirportSearchService service, AirportIndex index) {
        NaiveBaseline baseline = new NaiveBaseline(index);
        List<EvalCase> cases = Evaluator.loadGoldenSet();

        int pass = 0, baselinePass = 0, baselineOverreach = 0;
        System.out.println("\n=== Golden-set scorecard (ours vs naive substring baseline) ===\n");
        System.out.printf("%-4s %-12s %-7s %-9s  %s%n", "", "query", "ours", "baseline", "note");

        for (EvalCase c : cases) {
            Evaluator.CaseResult res = Evaluator.evaluate(service, c);
            boolean basePass = baselinePasses(baseline, c);
            if (res.passed()) pass++;
            if (basePass) baselinePass++;
            if (baselineHasOverreach(baseline, c)) baselineOverreach++;

            System.out.printf("%-4s %-12s %-7s %-9s  %s%n",
                    res.passed() ? "PASS" : "FAIL",
                    truncate(c.query, 12),
                    res.passed() ? "ok" : "MISS",
                    basePass ? "ok" : "miss",
                    c.note == null ? "" : c.note);
            if (!res.passed()) {
                res.failures().forEach(f -> System.out.println("       - " + f));
            }
        }

        int n = cases.size();
        System.out.println("\n---------------------------------------------------------------");
        System.out.printf("Ours     : %d/%d passed (%.0f%%)%n", pass, n, 100.0 * pass / n);
        System.out.printf("Baseline : %d/%d passed (%.0f%%), with %d overreach violations%n",
                baselinePass, n, 100.0 * baselinePass / n, baselineOverreach);
        System.out.println("---------------------------------------------------------------");

        if (pass < n) {
            System.out.println("\nFAILURES PRESENT — see above.");
            System.exit(1);
        }
    }

    /** Apply the same expectations to the baseline's flat code list (its best-effort interpretation). */
    private static boolean baselinePasses(NaiveBaseline baseline, EvalCase c) {
        Set<String> codes = new java.util.LinkedHashSet<>(baseline.search(c.query));
        String top = codes.isEmpty() ? null : codes.iterator().next();
        if (c.expectTopIata != null && !c.expectTopIata.equals(top)) return false;
        if (c.expectTopType != null) return false; // baseline has no notion of grouping/type
        for (String code : c.expectContainsIata) if (!codes.contains(code)) return false;
        for (String code : c.mustNotContainIata) if (codes.contains(code)) return false;
        return true;
    }

    private static boolean baselineHasOverreach(NaiveBaseline baseline, EvalCase c) {
        if (c.mustNotContainIata.isEmpty()) return false;
        Set<String> codes = new java.util.LinkedHashSet<>(baseline.search(c.query));
        return c.mustNotContainIata.stream().anyMatch(codes::contains);
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private SearchHarness() {}
}
