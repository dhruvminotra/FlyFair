package com.flyfair.search.ingest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * ONE-OFF build step (not on the query path). Downloads the raw OurAirports dataset (once),
 * prunes it to commercial airports only, resolves region/country names, and writes the small,
 * committed snapshot {@code src/main/resources/data/airports.csv}.
 *
 * <p>"Most airport databases are junk" — this is where we throw the junk away and *show* it:
 * it prints kept/dropped counts by reason so the cleaning is demonstrated, not just claimed.
 *
 * <p>Run: {@code mvn exec:java -Dexec.mainClass=com.flyfair.search.ingest.AirportDataPruner}
 */
public final class AirportDataPruner {

    private static final String BASE = "https://davidmegginson.github.io/ourairports-data/";
    private static final Path CACHE = Path.of(System.getProperty("java.io.tmpdir"), "flyfair-data");
    private static final Path OUTPUT = Path.of("src/main/resources/data/airports.csv");

    /** Airport types that are never commercial passenger service. */
    private static final Set<String> JUNK_TYPES =
            Set.of("heliport", "seaplane_base", "balloonport", "closed");

    public static void main(String[] args) throws Exception {
        Path airportsRaw = ensureCached("airports.csv");
        Path regionsRaw = ensureCached("regions.csv");
        Path countriesRaw = ensureCached("countries.csv");

        Map<String, String> regionNames = loadLookup(regionsRaw, "code", "name");
        Map<String, String> countryNames = loadLookup(countriesRaw, "code", "name");

        Map<String, Integer> dropped = new TreeMap<>();
        int kept = 0, total = 0;

        Files.createDirectories(OUTPUT.getParent());
        try (CSVParser parser = CSVParser.parse(Files.newBufferedReader(airportsRaw, StandardCharsets.UTF_8),
                     CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build());
             CSVPrinter out = new CSVPrinter(Files.newBufferedWriter(OUTPUT, StandardCharsets.UTF_8),
                     CSVFormat.DEFAULT.builder().setHeader(
                             "iata", "icao", "type", "name", "city",
                             "iso_region", "region_name", "iso_country", "country",
                             "lat", "lon", "importance").build())) {

            for (CSVRecord r : parser) {
                total++;
                String type = r.get("type");
                String iata = r.get("iata_code").trim();
                String scheduled = r.get("scheduled_service").trim();

                String drop = dropReason(type, iata, scheduled);
                if (drop != null) {
                    dropped.merge(drop, 1, Integer::sum);
                    continue;
                }

                String isoRegion = r.get("iso_region");
                String isoCountry = r.get("iso_country");
                out.printRecord(
                        iata.toUpperCase(),
                        r.get("ident"),
                        type,
                        r.get("name"),
                        r.get("municipality"),
                        isoRegion,
                        regionNames.getOrDefault(isoRegion, ""),
                        isoCountry,
                        countryNames.getOrDefault(isoCountry, ""),
                        r.get("latitude_deg"),
                        r.get("longitude_deg"),
                        importance(type, scheduled));
                kept++;
            }
        }

        int totalDropped = dropped.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("%nOurAirports prune complete -> %s%n", OUTPUT.toAbsolutePath());
        System.out.printf("  scanned : %,d%n", total);
        System.out.printf("  kept    : %,d  (commercial airports with valid IATA)%n", kept);
        System.out.printf("  dropped : %,d%n", totalDropped);
        dropped.forEach((reason, n) -> System.out.printf("            - %-18s %,d%n", reason, n));
    }

    /** @return a drop reason, or null to keep. */
    private static String dropReason(String type, String iata, String scheduled) {
        if (JUNK_TYPES.contains(type)) return type;                 // heliport / seaplane_base / ...
        if (!isValidIata(iata)) return "no-iata";                   // military / private / GA fall out here
        if ("small_airport".equals(type) && !"yes".equals(scheduled)) return "small-no-service";
        return null;
    }

    private static boolean isValidIata(String iata) {
        if (iata == null || iata.length() != 3) return false;
        for (int i = 0; i < 3; i++) {
            if (!Character.isLetter(iata.charAt(i))) return false;
        }
        return true;
    }

    /** Bigger = more important; used only as a tie-breaker prior in ranking. */
    private static int importance(String type, String scheduled) {
        int base = switch (type) {
            case "large_airport" -> 30;
            case "medium_airport" -> 20;
            default -> 10;                 // small_airport (kept only if scheduled)
        };
        return base + ("yes".equals(scheduled) ? 5 : 0);
    }

    private static Map<String, String> loadLookup(Path csv, String keyCol, String valCol) throws IOException {
        Map<String, String> map = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader,
                     CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build())) {
            for (CSVRecord r : parser) {
                map.put(r.get(keyCol), r.get(valCol));
            }
        }
        return map;
    }

    /** Downloads the file into the temp cache if not already present. */
    private static Path ensureCached(String file) throws Exception {
        Files.createDirectories(CACHE);
        Path local = CACHE.resolve(file);
        if (Files.exists(local) && Files.size(local) > 0) {
            return local;
        }
        System.out.println("Downloading " + BASE + file + " ...");
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<Path> resp = client.send(
                HttpRequest.newBuilder(URI.create(BASE + file)).build(),
                HttpResponse.BodyHandlers.ofFile(local));
        if (resp.statusCode() != 200) {
            throw new IOException("Failed to download " + file + ": HTTP " + resp.statusCode());
        }
        return local;
    }

    private AirportDataPruner() {}
}
