package com.flyfair.search.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flyfair.search.index.AirportIndex;
import com.flyfair.search.model.Airport;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the committed, pruned snapshot and the curated overlays into an {@link AirportIndex}.
 * Runtime only — no network. Everything it reads ships on the classpath under {@code /data}.
 *
 * <p>Crucially, every IATA code referenced by the hand-curated alias/multilingual files is
 * validated against the airports actually present. An invented or typo'd code (e.g. a wrong
 * code an LLM produced while drafting the lists) is rejected and reported, never silently
 * indexed. That validation is our regression guard against bad reference data.
 */
public final class DataLoader {

    private static final String AIRPORTS_CSV = "/data/airports.csv";
    private static final String ALIASES_JSON = "/data/aliases.json";
    private static final String MULTILINGUAL_JSON = "/data/multilingual.json";

    private final List<String> rejectedCodes = new ArrayList<>();

    /** Build the index from the packaged snapshot + curated overlays. */
    public AirportIndex load() {
        AirportIndex index = new AirportIndex();
        loadAirports(index);
        loadAliases(index, ALIASES_JSON);
        loadAliases(index, MULTILINGUAL_JSON);
        return index;
    }

    public List<String> rejectedCodes() { return rejectedCodes; }

    private void loadAirports(AirportIndex index) {
        try (InputStream in = open(AIRPORTS_CSV);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader,
                     CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord r : parser) {
                index.addAirport(new Airport(
                        r.get("iata"),
                        r.get("icao"),
                        r.get("type"),
                        r.get("name"),
                        r.get("city"),
                        r.get("iso_region"),
                        r.get("region_name"),
                        r.get("iso_country"),
                        r.get("country"),
                        parseDouble(r.get("lat")),
                        parseDouble(r.get("lon")),
                        Integer.parseInt(r.get("importance"))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + AIRPORTS_CSV, e);
        }
    }

    /** Curated files are {@code term -> [IATA, ...]}; each code is validated against the index. */
    private void loadAliases(AirportIndex index, String resource) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<String>> entries;
        try (InputStream in = open(resource)) {
            entries = mapper.readValue(in, new TypeReference<LinkedHashMap<String, List<String>>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + resource, e);
        }
        for (Map.Entry<String, List<String>> e : entries.entrySet()) {
            String term = e.getKey();
            for (String code : e.getValue()) {
                Airport a = index.byIata(code.trim().toUpperCase());
                if (a == null) {
                    rejectedCodes.add(resource + ": \"" + term + "\" -> " + code + " (no such airport)");
                    continue;
                }
                index.addAlias(term, a);
            }
        }
    }

    private static double parseDouble(String s) {
        try { return s == null || s.isBlank() ? 0.0 : Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static InputStream open(String resource) {
        InputStream in = DataLoader.class.getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing classpath resource " + resource
                    + " — run the AirportDataPruner once to generate the snapshot.");
        }
        return in;
    }
}
