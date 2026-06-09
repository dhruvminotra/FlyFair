package com.flyfair.search.harness;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** One golden-set expectation, deserialized from {@code /data/golden_set.json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class EvalCase {
    public String query;
    public String note;
    public String expectTopIata;                 // result[0].primary() must be this code
    public String expectTopType;                 // result[0] LocationType (e.g. REGION, MULTI_AIRPORT_CITY)
    public List<String> expectContainsIata = List.of(); // union of all result codes must contain these
    public List<String> mustNotContainIata = List.of(); // ...and must contain none of these

    @Override
    public String toString() {
        return query + (note == null ? "" : " (" + note + ")");
    }
}
