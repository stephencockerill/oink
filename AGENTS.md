# AGENTS.md

Entry point for AI agents (Claude, Codex, etc.) working in this repository. Detailed conventions live in the linked `docs/`.

---

## What this repo is

`oink` is an Android app (`com.oink.app`) - a workout habit tracker that uses behavioral economics (loss aversion, the ÷2 halving penalty, streak freezes) to build consistent exercise habits. Users earn a real fun-money budget by working out and cash it out when ready.

Single module, **manual DI** (no Hilt) via `OinkApplication` + provider objects. Stack: Kotlin 2.0 / Jetpack Compose (Material3), Room + KSP, DataStore Preferences, WorkManager, Glance widgets, Navigation Compose. Pattern is **MVVM + Repository** with `StateFlow`-backed UI state. `minSdk 26`, `compileSdk`/`targetSdk 35`, Java/JVM 17.

See [`README.md`](README.md) for the product and [`ROADMAP.md`](ROADMAP.md) for direction.

---

## Foundational reading

- [`docs/android.md`](docs/android.md) - Android/Kotlin/Compose conventions and this repo's idioms. Read before touching code.
- [`docs/habit-psychology.md`](docs/habit-psychology.md) - the behavioral-economics principles the product is built on. Read before designing or changing any user-facing feature.
- [`docs/multi-habit-plan.md`](docs/multi-habit-plan.md) - in-flight design notes for the multi-habit refactor. Treat as a draft, not a rule.

---

## Routing map

| You're touching | Read first | Also consult |
|---|---|---|
| Compose UI, ViewModels, coroutines/Flow, Room, DataStore, WorkManager, Glance, Navigation, Gradle/manifest | [`docs/android.md`](docs/android.md) | **`android-expert` agent** (see below) |
| Any user-facing feature, copy, animation, or reward mechanic | [`docs/habit-psychology.md`](docs/habit-psychology.md) | |
| Balance / check-in / cash-out / freeze logic | both docs above | `android-expert` for the persistence + concurrency correctness |

---

## Android work → consult the android-expert agent

**Whenever a change involves Android development specifically - Compose UI, ViewModels/state, coroutines & Flow, Room, DataStore, WorkManager, Glance widgets, Navigation, or Gradle/manifest config - hand the design, implementation, or review to the `android-expert` subagent.**

Its definition is committed at [`.claude/agents/android-expert.md`](.claude/agents/android-expert.md). It carries the authoritative, cited Android/Kotlin canon plus this repo's specifics, and it corrects antipatterns rather than conforming to existing code. `docs/android.md` is the quick reference; the agent is the authority. Don't hand-wave Android decisions that the agent should make.

---

## Workflow

Scale this to the task - trivial edits skip most of it.

1. **Route via the map** - read the relevant `docs/` file before opening source.
2. **Delegate Android work** to the `android-expert` agent per the rule above.
3. **Match repo idioms** - manual DI (provider objects), `StateFlow` UI state, absolute imports, fakes for tests. When in doubt, copy a recent canonical file.
4. **Test** - new behavior gets tests; bug fixes get regression tests. Repo/VM tests use fakes + `kotlinx-coroutines-test`; schema changes get a `MigrationTestHelper` migration test.
5. **Verify before finishing** - build and run the affected flow, not just tests.

---

## Hard rules

These cannot be overridden without explicit human sign-off:

1. **Never commit secrets.** `local.properties` is git-ignored; keep keys out of source, VCS, and the manifest.
2. **Money is integer minor units (`Long` cents), never `Double`/`Float`.**
3. **Every Room schema change ships a migration and a migration test.** Never reintroduce `fallbackToDestructiveMigration()` outside dev - it wipes user data. `exportSchema=true` and the committed schema JSON under `app/schemas/` stay that way.
4. **Dependency versions live only in `gradle/libs.versions.toml`** - never hardcode a version in `build.gradle.kts`.
5. **Never push directly to `main`.** PRs only.
