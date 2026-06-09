# Fly Fairly — Airport Search

A small, deterministic airport-search engine for the Fly Fairly take-home. Custom in-memory tiered
index over ~5,300 commercial airports — no external search engine, no runtime LLM. Handles IATA/city
codes both directions, region/state expansion, multi-airport metros, same-name disambiguation,
multi-script queries (EN/中文/日本語/한국어/العربية), accents, and bounded typo tolerance.

See **[MEMO.md](MEMO.md)** for the approach, decisions, and trade-offs.

## Requirements
- **Java 17** and Maven.
  ```
  export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
  ```

> Note: the `compile` below is required after a fresh checkout or `mvn clean` (the `target/`
> build folder is git-ignored). Once compiled, you can drop `compile` on later runs.

## Run the eval (proves it beats a naive baseline)
```
mvn -q compile exec:java
```
Prints a per-case scorecard and the summary: **ours 29/29 vs naive substring 9/29**.

## Run a single query
```
mvn -q compile exec:java -Dexec.args="Hawaii"
mvn -q compile exec:java -Dexec.args="Bali"
mvn -q compile exec:java -Dexec.args="東京"
mvn -q compile exec:java -Dexec.args="London"
```

## Run the web UI
```
mvn -q compile exec:java -Dexec.mainClass=com.flyfair.search.web.HttpSearchServer
# then open http://localhost:8080
```
A search-as-you-type page backed by the engine. Each result shows its airports plus the match tier
(exact IATA, alias, region/country expansion, fuzzy, …). Fully local — no API key, no network.

## Run the tests (regression guard)
```
mvn test
```
The golden set (`src/main/resources/data/golden_set.json`) encodes every failure case from the brief;
a regression turns the build red.

## Regenerate the data snapshot (one-off, needs network)
```
mvn -q compile exec:java -Dexec.mainClass=com.flyfair.search.ingest.AirportDataPruner
```
Downloads OurAirports once, prunes to commercial-only, and rewrites the committed
`src/main/resources/data/airports.csv`, printing kept/dropped counts.

## Layout
```
src/main/java/com/flyfair/search/
  model/      Airport, LocationType, SearchResult, DataSource
  normalize/  TextNormalizer (ICU: diacritics, CJK, Arabic)
  index/      AirportIndex (purpose-built maps)
  rank/       Ranker (tiers + tie-break), FuzzyMatcher (bounded, guarded)
  search/     AirportSearchService (orchestration)
  data/       DataLoader (snapshot + curated overlays, with IATA validation)
  ingest/     AirportDataPruner (one-off build step)
  harness/    SearchHarness (CLI), Evaluator, EvalCase, NaiveBaseline
  web/        HttpSearchServer (search UI)
src/main/resources/data/
  airports.csv        pruned commercial-airport snapshot (committed)
  aliases.json        curated friendly/tourism/metro aliases
  multilingual.json   curated script -> city mappings
  golden_set.json     eval expectations
```
