package com.flyfair.search;

import com.flyfair.search.data.DataLoader;
import com.flyfair.search.harness.EvalCase;
import com.flyfair.search.harness.Evaluator;
import com.flyfair.search.index.AirportIndex;
import com.flyfair.search.search.AirportSearchService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The regression guard: every production failure case from the brief, encoded as a golden
 * expectation, must pass. If a future change regresses one, the build goes red.
 */
class GoldenSetTest {

    private static AirportSearchService service;

    @BeforeAll
    static void setUp() {
        DataLoader loader = new DataLoader();
        AirportIndex index = loader.load();
        // The curated overlays must reference only real airports.
        assertEquals(List.of(), loader.rejectedCodes(),
                "curated files reference unknown IATA codes: " + loader.rejectedCodes());
        service = new AirportSearchService(index);
    }

    static List<EvalCase> cases() {
        return Evaluator.loadGoldenSet();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void goldenCase(EvalCase c) {
        Evaluator.CaseResult res = Evaluator.evaluate(service, c);
        assertTrue(res.passed(),
                () -> "query \"" + c.query + "\" (" + c.note + ") failed: " + res.failures()
                        + " | results=" + res.unionCodes());
    }
}
