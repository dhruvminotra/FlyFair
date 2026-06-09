package com.flyfair.search.harness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyfair.search.model.SearchResult;
import com.flyfair.search.search.AirportSearchService;

import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Loads the golden set and scores the search service against it. Shared by the CLI and the tests. */
public final class Evaluator {

    /** Outcome of evaluating one case: whether it passed and, if not, why. */
    public record CaseResult(EvalCase expectation, boolean passed, List<String> failures,
                             Set<String> unionCodes, String topIata, String topType) {}

    private Evaluator() {}

    public static List<EvalCase> loadGoldenSet() {
        try (InputStream in = Evaluator.class.getResourceAsStream("/data/golden_set.json")) {
            if (in == null) throw new IllegalStateException("Missing /data/golden_set.json");
            return new ObjectMapper().readValue(in, new TypeReference<List<EvalCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static CaseResult evaluate(AirportSearchService service, EvalCase c) {
        List<SearchResult> results = service.search(c.query, 10);
        Set<String> union = unionCodes(results);
        String topIata = results.isEmpty() ? null : results.get(0).primary().iata();
        String topType = results.isEmpty() ? null : results.get(0).locationType().name();

        List<String> failures = new ArrayList<>();
        if (c.expectTopIata != null && !c.expectTopIata.equals(topIata)) {
            failures.add("top expected " + c.expectTopIata + " but was " + topIata);
        }
        if (c.expectTopType != null && !c.expectTopType.equals(topType)) {
            failures.add("top type expected " + c.expectTopType + " but was " + topType);
        }
        for (String code : c.expectContainsIata) {
            if (!union.contains(code)) failures.add("missing expected " + code);
        }
        for (String code : c.mustNotContainIata) {
            if (union.contains(code)) failures.add("contains forbidden " + code);
        }
        return new CaseResult(c, failures.isEmpty(), failures, union, topIata, topType);
    }

    public static Set<String> unionCodes(List<SearchResult> results) {
        Set<String> codes = new LinkedHashSet<>();
        results.forEach(r -> r.airports().forEach(a -> codes.add(a.iata())));
        return codes;
    }
}
