# Nemesis (Android)

A mobile port of [Nemesis](https://github.com/ckirby04/Nemesis), an exploitative poker EV simulator. Describe your
low-stakes table in plain English, and Nemesis models the opponent pool and Monte-Carlo-simulates which starting
hands make money against it, position by position, on a 13×13 heatmap.

It is **not a GTO solver**: villains play the fixed strategy you describe, and hero plays a defined open-and-value-bet
line. Results are directional, not exact bb/100 — a study tool for between sessions.

## Features

- **Analyze** — plain-English table description → villain model → full 169-hand simulation per position, with live
  progress. Runs in an app-scoped coroutine, so it survives tab switches and rotation.
- **Results** — interactive heatmap (tap a cell for EV ± 95% CI); hands whose interval crosses zero are hatched as noise;
  ranked best/worst/too-close lists.
- **Advisor** — retrieval-augmented report: the sim's per-class EV profile retrieves matching strategy principles
  (TF-IDF over a built-in knowledge base) that ground the written report. Ask follow-up questions; share the report.
- **Live table** — per-seat reads in plain English ("the guy on my left calls everything", "UTG is a nit") or archetype
  presets; runs can use them as per-seat villain models.
- **History** — every run is saved with its results, report, and follow-ups (swipe to delete, with undo).
- **Settings** — optional Anthropic API key and model, sims per hand, full grid vs 41-hand sample, rake model.

## Claude is optional

With no API key the app runs fully offline: descriptions and notes are parsed by keyword heuristics, and reports use a
deterministic template. Add an Anthropic API key in Settings to have Claude parse descriptions and write reports and
answers. The key is stored only on the device and excluded from backups.

## Architecture

| Layer | Contents |
|---|---|
| `engine/` | Card evaluator (treys-compatible ranks), hand utilities, preflop ranking, villain/rake models, Monte-Carlo simulator |
| `parsing/` | Offline description/seat-note parsers, archetypes, `TableState` |
| `advisor/` | Knowledge base, TF-IDF retriever, profile extraction, prompts, offline report |
| `data/` | Room (`runs` table), DataStore (settings, API key, live table), `ClaudeService` (Anthropic Java SDK), `CoachRepository` (Claude with offline fallback), `SimulationRunner` |
| `ui/` | Jetpack Compose + Material 3 screens, each with a ViewModel; type-safe Navigation Compose routes |

Dependencies are wired by hand in `AppContainer` (`NemesisApplication.kt`).

## Build and test

Open the project in Android Studio and run the `app` configuration (min SDK 30). From a terminal (uses Android Studio's
bundled JDK if no system Java is installed):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest   # engine parity + advisor tests
./gradlew :app:assembleDebug
```

The engine tests are ported from the original Python test suite (hand normalization, evaluator extremes, rake,
seeded reproducibility, ordering invariants).
