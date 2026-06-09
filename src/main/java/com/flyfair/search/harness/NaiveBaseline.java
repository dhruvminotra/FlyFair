package com.flyfair.search.harness;

import com.flyfair.search.index.AirportIndex;
import com.flyfair.search.model.Airport;
import com.flyfair.search.normalize.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * The straw man: naive case-insensitive substring search over airport + city names. This is what
 * "index every field and hope" looks like, and the eval exists to prove the real service beats it.
 *
 * <p>Its failure modes are exactly the ones in the brief: "Bali" substring-matches "Balikpapan",
 * "Florida" matches "La Florida", "TUL"/"BAH" (codes) and "東京"/"دبي" (other scripts) return
 * nothing because it only looks at the Latin name field.
 */
public final class NaiveBaseline {

    private final AirportIndex index;

    public NaiveBaseline(AirportIndex index) {
        this.index = index;
    }

    /** @return IATA codes of airports whose normalized name or city contains the query substring. */
    public List<String> search(String query) {
        String q = TextNormalizer.normalize(query);
        List<String> hits = new ArrayList<>();
        if (q.isEmpty()) return hits;
        for (Airport a : index.all()) {
            String name = TextNormalizer.normalize(a.name());
            String city = TextNormalizer.normalize(a.city());
            if (name.contains(q) || city.contains(q)) {
                hits.add(a.iata());
            }
        }
        return hits;
    }
}
