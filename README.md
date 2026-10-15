# Nemesis (Android)

[![CI](https://github.com/ckirby04/nemesis-android-application/actions/workflows/ci.yml/badge.svg)](https://github.com/ckirby04/nemesis-android-application/actions/workflows/ci.yml)

A mobile port of [Nemesis](https://github.com/ckirby04/Nemesis), an exploitative poker EV simulator. Describe your
low-stakes table in plain English, and Nemesis models the opponent pool and Monte-Carlo-simulates which starting
hands make money against it, position by position, on a 13×13 heatmap.

It is **not a GTO solver**: villains play the fixed strategy you describe, and hero plays a defined open-and-value-bet
line. Results are directional, not exact bb/100 — a study tool for between sessions.

## Features

- **Analyze** — plain-English table description → villain model → full 169-hand simulation per position, with live
  progress. Runs as a foreground service with a progress notification (and Cancel), so it keeps going if you leave
  the app; a "Results ready" notification opens the run when it finishes.
- **Results** — interactive heatmap (tap a cell for EV ± 95% CI); hands whose interval crosses zero are hatched as noise;
  ranked best/worst/too-close lists.
- **Advisor** — retrieval-augmented report: the sim's per-class EV profile retrieves matching strategy principles
  (TF-IDF over a built-in knowledge base) that ground the written report. Ask follow-up questions; share the report.
- **Live table** — per-seat reads in plain English ("the guy on my left calls everything", "UTG+1 is a nit") or
  archetype presets; runs can use them as per-seat villain models. Full ring is 9 seats (UTG, UTG+1, MP, LJ, HJ, CO,
  BTN, SB, BB); short-handed tables drop the early seats so the button and blinds are always there.
- **History** — every run is saved with its results, report, and follow-ups (swipe to delete, with undo).
- **Settings** — optional Anthropic API key and model, sims per hand, full grid vs 41-hand sample, rake model.

## Claude is optional

With no API key the app runs fully offline: descriptions and notes are parsed by keyword heuristics, and reports use a
deterministic template. With a key, Claude parses descriptions and notes and writes reports and follow-up answers.

There are two ways to give it a key:

- **In the app** (any build): Settings → Claude → paste the key → Save. It's stored only on the device and excluded
  from backups.
- **For development** (debug builds only): add a line to `local.properties` in the project root, which is git-ignored:

  ```properties
  anthropic.apiKey=sk-ant-...
  ```

  Debug builds use it whenever no key is saved in Settings; release builds never contain it.

## Architecture

| Layer | Contents |
|---|---|
| `engine/` | Card evaluator (treys-compatible ranks), hand utilities, preflop ranking, villain/rake models, Monte-Carlo simulator. Postflop matches the Python engine; preflop is more realistic (only seats behind hero act, blinds count toward their bets, results are measured against folding, 3-bet pots are priced properly, no BB opens) |
| `parsing/` | Offline description/seat-note parsers, archetypes, `TableState` |
| `advisor/` | Knowledge base, TF-IDF retriever, profile extraction, prompts, offline report |
| `data/` | Room (`runs` table), DataStore (settings, API key, live table), `ClaudeService` (Anthropic Java SDK), `CoachRepository` (Claude with offline fallback), `SimulationRunner` |
| `sim/` | `SimulationService` (foreground service, `shortService` type) and its notifications |
| `ui/` | Jetpack Compose + Material 3 screens, each with a ViewModel; type-safe Navigation Compose routes |

Dependencies are wired by hand in `AppContainer` (`NemesisApplication.kt`).

## Build and test

Open the project in Android Studio and run the `app` configuration (min SDK 30). From a terminal (uses Android Studio's
bundled JDK if no system Java is installed):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest            # JVM: engine parity, parsing, advisor
./gradlew :app:connectedDebugAndroidTest    # device/emulator: Compose UI flows, heatmap, Room/DataStore, service
./gradlew :app:assembleDebug :app:assembleRelease
```

- **Unit tests** (`app/src/test`): engine tests ported from the original Python suite (hand normalization, evaluator,
  rake, seeded reproducibility, ordering invariants), seat/parsing rules, and the advisor.
- **Instrumented tests** (`app/src/androidTest`): end-to-end Compose flows (navigation, run → results → report →
  follow-up, history, live table, settings), heatmap hit-testing and Markdown rendering, Room/DataStore repositories,
  and the foreground service (runs in the foreground, Cancel stops it, stops itself when done). Each test gets a fresh
  in-memory database and throwaway settings via a custom test runner, so tests never touch real app data.

GitHub Actions runs both suites on every push (instrumented tests on an API 35 emulator) and uploads the release APK
as a build artifact. The release build is signed with the debug key so it installs anywhere for grading; use a real
keystore before publishing to a store.
