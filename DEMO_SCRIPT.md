# Recorded Walkthrough — Script (10–15 min)

> Goal: walk a senior engineer through the work. They watch this *before* reading code.

## 0. Setup (15s)
Terminal ready, JDK 17. One-liner to set env:
```
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
```

## 1. Framing — the judgment call (1.5 min)
- The brief is "fix search." The hard part isn't scale (it's ~5k commercial airports) — it's **ranking
  judgment**: disambiguation, overreach suppression, region/metro expansion, cross-script, typos.
- So I **didn't reach for Elasticsearch/Typesense**: their default fuzziness *causes* Bali→Balikpapan,
  and I'd hand-build the hard 20% anyway. A custom in-memory index is the least code that controls the
  exact thing being graded — fast, deterministic, demo-safe, no server, no runtime LLM.

## 2. Data cleaning — "most airport DBs are junk" (2 min)
- Show `AirportDataPruner`. Run it live:
```
mvn -q exec:java -Dexec.mainClass=com.flyfair.search.ingest.AirportDataPruner
```
- Point at the printed counts: **85,519 → 5,340**, dropping heliports/closed/seaplane/military(no-IATA).
  Emphasize the snapshot is small + committed; runtime is offline.
- Show region search is *derived* from `iso_region` (Hawaii = `US-HI`) — no hand-built region file.

## 3. The eval scorecard — prove it beats naive (2.5 min)
```
mvn -q exec:java        # runs the golden set vs the naive substring baseline
```
- Read the bottom line: **ours 28/28 (100%) vs baseline 9/28 (32%), 2 overreach violations**.
- Call out specific baseline misses: codes (TUL/BAH), scripts (東京/دبي), region (Hawaii), and the
  overreach (Bali→Balikpapan, Florida→La Florida) the baseline commits and we don't.

## 4. Live failure cases (3.5 min) — run these one by one
```
mvn -q exec:java -Dexec.args="Hawaii"     # REGION_EXPANSION -> HNL,OGG,KOA,LIH...
mvn -q exec:java -Dexec.args="Bali"       # ALIAS -> DPS, and NOT Balikpapan
mvn -q exec:java -Dexec.args="Florida"    # REGION -> US-FL, never La Florida (Chile)
mvn -q exec:java -Dexec.args="LON"        # MULTI_AIRPORT_CITY -> LHR,LGW,STN,LCY,LTN
mvn -q exec:java -Dexec.args="London"     # disambiguation: UK group / Ontario / Kentucky
mvn -q exec:java -Dexec.args="東京"        # ALIAS -> HND,NRT  (real Japanese name, not MT)
mvn -q exec:java -Dexec.args="دبي"        # ALIAS -> DXB
mvn -q exec:java -Dexec.args="Londn"      # FUZZY -> London (typo), bounded
```
- For each, point at the `MatchType` tier and `LocationType` in the output — explain *why* it matched.

## 5. Architecture tour (3 min)
- `TextNormalizer` — NFKD + diacritic strip + Arabic/CJK handling (one place; show São Paulo test).
- `AirportIndex` — purpose-built maps (mirrors the reference platform's `LocationManager`).
- `AirportSearchService` — tiered candidate generation + **coverage suppression**; the
  "only-fuzz-when-nothing-exact-matched" rule is the key overreach guard.
- `FuzzyMatcher` — bounded Damerau-Levenshtein + length floor (why "Bali" can't reach "Balikpapan").
- `DataLoader` validation — show `rejectedCodes`: how a wrong/LLM-hallucinated IATA gets caught.

## 6. Where I used LLMs + where it was wrong (1.5 min)
- Claude scaffolded code, drafted the overlays + golden set.
- Two concrete catches: it gave Tokyo as HND-only (data showed NRT≠Tokyo municipality → explicit alias);
  it invented secondary IATA codes → I added the load-time validator, now a permanent guard.

## 7. One thing I'd change with another week (30s)
- A real popularity prior (passenger volume) so within-city ordering (LHR vs LGW) reflects reality and
  disambiguation ranks better; broader auto-generated + auto-validated multilingual coverage.
```
