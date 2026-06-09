# Fly Fairly — Airport Search: Approach Memo

**Scope cut:** a small, sharp, *fully deterministic* airport search that nails every failure case in
the brief and proves it with an eval harness, rather than a sprawling half-built platform. ~5,300
commercial airports, in-memory, no server, no runtime LLM. **28/28 golden cases pass; the naive
substring baseline passes 9/28 with 2 overreach violations.**

## Data: where it came from and what I did to it
- **Source: OurAirports** (`airports.csv`, public domain, no key). It carries the exact fields needed
  to throw away junk: `type`, `scheduled_service`, `iata_code`, `municipality`, `iso_region`,
  `iso_country`.
- **Cleaning is a one-off build step** (`AirportDataPruner`) that downloads once and writes a small,
  committed snapshot. "Most airport DBs are junk," so I *show* the pruning — it prints counts:
  **scanned 85,519 → kept 5,340**, dropping heliports (23,059), closed (13,197), no-IATA (39,135 — this
  is where military/GA fall out, as they have no commercial service and no IATA code), seaplane bases
  (1,263) and small airports with no scheduled service (3,464).
- **Region search is derived, not hand-built**: `iso_region` (e.g. `US-HI`) → region name (Hawaii), so
  "Hawaii"/"Ontario"/"Florida" expand to their airports for free.
- **Two tiny hand-curated overlays** fill the gaps the raw data can't: `aliases.json` (friendly/tourism/
  metro: Brussels→BRU since its city is *Zaventem*; Bali→DPS; LON/NYC metro groups; Roma→Rome) and
  `multilingual.json` (東京/北京/서울/دبي/München → codes). Every code in both is **validated against the
  pruned set at load** — an invalid code is rejected, never indexed.

## Search approach: what I evaluated and why this one
The dataset is **small and static**; the hard part is **ranking judgment**, not scale or raw text
search. So I built a **custom in-memory tiered index** rather than reach for an engine:
- **Elasticsearch** — heavy infra/ops for ~5k static rows; tuning analyzers to beat these specific
  cases is more work than coding them, and it hides the craft being graded.
- **Typesense/Meilisearch** — their typo-tolerant defaults are exactly what *cause* Bali→Balikpapan and
  Florida→La Florida; I'd still hand-build disambiguation/region/metro on top, plus carry a server.
- **Postgres FTS** — weak CJK tokenization, clunky rank tuning, a DB dependency for static data.
- **LLM at query time** — latency, cost, non-determinism, hallucinated codes; wrong tool for a
  per-keystroke box. (I use the LLM offline instead.)

**Ranking** is tiered (exact IATA → ICAO → alias/multilingual → exact name/city → region expansion →
prefix → guarded fuzzy); tier dominates, airport `importance` only breaks ties. Two guards do the heavy
lifting against overreach: **fuzzy runs only when nothing exact matched**, and **edit distance is
bounded with a length floor** (queries <5 chars are never fuzzed, so "Bali" can't reach "Balikpapan").
"Translation" is alternate-name *lookup*, not machine translation: 東京 is indexed as Tokyo's real name.

## LLMs/tools, prompt iteration, and where the LLM was wrong
- **Claude (via Claude Code)** to scaffold the Java, draft the curated overlays and the golden set, and
  to reason about prune rules. **Cursor/Copilot-style** edits for the boilerplate.
- **Prompt iteration log (condensed):** (1) First overlay draft mixed up multi-airport cities — it gave
  `Tokyo→[HND]` only; the *data* showed NRT's municipality is "Narita", so I made the alias explicit
  `[HND,NRT]`. (2) It proposed plausible-but-wrong IATA codes for secondary airports; rather than trust
  them I added the **load-time validator**, which is now a permanent regression guard. (3) It wanted to
  build a Wikidata ingestion pipeline for multilingual names — I cut that as over-engineering for a 3–5h
  scope and hand-curated the handful of scripts instead.
- **Where it was wrong & how I caught it:** invented/!shifted codes were caught by validation against
  OurAirports; the Tokyo grouping bug was caught by the golden eval (`expectContainsIata:[HND,NRT]`).

## Build vs buy vs fake
- **Built:** normalization, the tiered index, ranking, fuzzy guards, the eval harness, and a small web
  UI — the parts under test.
- **Bought (free):** the OurAirports dataset.
- **Faked/curated honestly:** the ~15-line alias/multilingual overlays, clearly labelled `CURATED` and
  validated. This is the only place I hand-wrote data.
- **Benchmarked, not depended on:** I wired Google Flights' own location-search API (searchapi.io) into
  the UI as a *side-by-side reference*, not as the engine — outsourcing the ranking would defeat the
  exercise, add a paid runtime dependency on our core flow, and remove my control over results. Telling
  detail it surfaced: for **"Bali", Google's autocomplete returns Balikpapan (BPN) and Balice too; my
  engine returns only DPS** — i.e. my overreach guards make it *more* precise than the reference on that
  case. (The API key is read from `SEARCHAPI_KEY`, never committed.)

## Evaluating this in production
- **Metrics:** the harness already reports pass-rate, and `mustNotContain` (overreach) violations vs a
  baseline. In prod I'd add **MRR / precision@k** from click logs, **zero-result rate**, **p99 latency**,
  and per-locale breakdowns.
- **Failure modes I hunted:** code↔city asymmetry (TUL/Tulsa), municipality≠friendly name (Brussels),
  fuzzy overreach (Bali, Florida), cross-script (東京/دبي), accents (São Paulo), same-name
  disambiguation (London UK/ON/KY), multi-airport metros (LON).
- **Regression catch:** the golden set runs in JUnit (`mvn test`), so a regression fails the build.

## With more time / what I'd push back on
- Add a real **popularity/importance prior** (passenger volume) so within-city ordering reflects reality
  (today LHR vs LGW tie on type) and to rank disambiguation better.
- Broaden multilingual coverage by **batch-generating overlays offline and auto-validating** them, and
  add country-level expansion and synonym packs.
- **Push back:** "Londn should find London" is reasonable, but aggressive typo tolerance is a product
  risk — I'd want click data before loosening the bounds, because every loosened edit is a potential
  Bali→Balikpapan.
