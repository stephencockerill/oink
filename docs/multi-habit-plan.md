# Oink → Multi-Habit Tracker — Implementation Plan

Status: **planned, not started** (saved 2026-07-11 to resume later).
Companion visual version: `.lavish/oink-multihabit-plan.html` (open with `npx -y lavish-axi .lavish/oink-multihabit-plan.html`).

## Goal

Generalize Oink from one hard-coded workout habit into many independent habits.
Each habit has its own piggy-bank balance, streak, check-in history, and streak-freeze state, reusing the existing halving/freeze logic per-habit instead of globally.
Add a PIN-locked "Private" area for habits the user wants hidden.

Four required capabilities:

1. **Habit model** — replace the single workout entity with a `Habit` table (name, emoji, reward value, freeze settings, created date); per-habit balance, streak, and check-in history.
2. **Home = habit cards** — a scrollable list of habit cards (multiple pigs); each shows balance + streak; tap to check in or drill into detail.
3. **Add-habit flow** — name + emoji + reward amount, with the same freeze/reward settings the workout habit already has.
4. **Private habits** — a single generic `🔒 Private` tile, always shown, PIN-gated, revealing nothing until unlocked.

## Current state (verified against source)

The app assumes exactly one habit; there is no habit identifier anywhere.

- **Two Room tables.** `check_ins` (date is UNIQUE, no habit key) and `cash_outs` (no habit key). DB is v1 with `fallbackToDestructiveMigration()` (a TODO already flags removing this before production). `exportSchema = false`.
- **Global DataStore prefs.** A single `UserPreferences` holds `exerciseReward`, `availableFreezes`, `frozenDates`, `totalFreezeSpending`, and reminder time — all app-wide singletons.
- **One balance, one streak.** Balance = `checkInBalance − totalCashedOut − totalFreezeSpending` (`BalanceCalculator`). Halving, streak, and freeze logic all live in `CheckInRepository`, keyed to nothing.
- **MVVM + manual DI.** `MainViewModel` (home + check-in), `RewardsViewModel` (cash-out), `SettingsViewModel` (prefs). DI is wired by hand in `MainActivity` via `OinkApplication.database`.
- **Glance widget** reads the global balance/streak.
- **Tests exist** for the repos, view models, and `BalanceCalculator`, plus fakes (`FakeCheckInDao`, `FakeCashOutDao`, `FakePreferencesRepository`).

The core move: introduce a `Habit` entity and thread a `habitId` foreign key through `check_ins`, `cash_outs`, and every per-habit setting, then scope all existing logic to a single habit at a time.
The math does not change; what it runs over does.

## Target data model

A `Habit` row owns its check-ins, cash-outs, and frozen days via `habitId`.
Per-habit settings that currently live as DataStore singletons (reward, freezes, freeze-spending) move onto the `Habit` row.
Frozen days become their own table so a habit can freeze specific dates.
Truly app-wide settings (reminder time, the private PIN) stay in DataStore.

### `Habit` (new table)

| Column | Notes |
| --- | --- |
| `id` | PK, autogenerate |
| `name` | |
| `emoji` | |
| `rewardValue` | renamed from workout-specific `exerciseReward` |
| `availableFreezes` | moved off DataStore |
| `totalFreezeSpending` | moved off DataStore |
| `isPrivate` | drives the Private area |
| `sortOrder` | ordering of habit cards |
| `createdAt` | |

### `check_ins` (changed)

- **New:** `habitId` foreign key.
- **Changed:** unique index becomes `(habitId, date)` (was `date`).
- Unchanged: `date`, `didExercise`, `balanceAfter`.

### `cash_outs` (changed)

- **New:** `habitId` foreign key.
- Unchanged: `name`, `amount`, `emoji`, `balanceBefore`, `balanceAfter`, `exerciseRewardAtTime`.

### `frozen_days` (new table)

- `habitId`, `date`. Replaces the global `frozenDates` set in DataStore.

## Experience & navigation

Today the Home screen *is* the workout habit.
It becomes a list; the current single-habit screen becomes the per-habit **detail** view reached by tapping a card.

- **Home** — habit-card list (emoji, name, balance, streak; tap to check in / open detail) + the always-present `🔒 Private` tile + a `+` FAB.
- **Habit detail** — essentially today's `HomeScreen` parameterized by `habitId`: balance card, streak/freeze row, check-in section, freeze prompt. Reuse, don't rewrite.
- **Add habit** — name field, emoji picker, reward stepper, freeze toggle, private toggle.
- **Private unlock** — PIN entry → **Private habit list** (same card/detail composables as the public list, behind the gate).
- Calendar / History / Rewards become scoped to `habitId`, reached from a habit's detail screen.

## Private habits — design

The requirement is subtle: the `🔒 Private` tile must appear whether or not any private habits exist, so its mere presence never betrays that the user is hiding something.

Rules that fall out of "reveal nothing":

1. The tile is a static, always-present row — not conditional on `count(isPrivate) > 0`. Same position, same label, always.
2. One lock for all private habits. No per-habit locks, no badge showing how many are inside.
3. An unlocked-but-empty private list is normal and expected — it leaks nothing, so "I have no secrets" and "you didn't guess my PIN" look identical from outside.
4. Private habits are excluded from the home list, the widget, and any aggregate totals.

PIN handling:

- **Decided: salted hash + plaintext rows.** Store a salted hash of the PIN (never plaintext), compared in constant time; the PIN lives in DataStore, not Room. Private habit rows stay in the normal SQLite file.
- **First-run:** if no PIN is set, tapping the tile offers to create one. Creating a PIN reveals nothing about habits, so plausible deniability holds.
- **Unlock state is in-memory and ephemeral** — re-lock on app background / process death; no "stay unlocked" flag persisted.
- **Threat model, accepted:** defends against casual snooping (someone picking up your phone), not a forensic attacker with an unlocked device. SQLCipher-style at-rest encryption is explicitly out of scope for this change.

## Widget — design

**Decided: one configurable widget per habit.**
Each Glance widget instance gets a config screen (on placement and editable later) to pick which habit it highlights, storing that `habitId` in the instance's Glance state.
The user can place several widgets, one per habit.
The picker lists non-private habits only, so a private habit can never surface on the launcher.

## Phased work breakdown

Ordered so the app stays buildable at each phase.

| # | Phase | What happens | Key files |
| --- | --- | --- | --- |
| 1 | **Habit entity & DB migration** | Add `Habit` entity + `HabitDao` + `HabitRepository` and `FrozenDay` table. Add `habitId` to `check_ins`/`cash_outs`; change unique index to `(habitId, date)`. Bump DB to v2, write `MIGRATION_1_2`, seed a default "Workout" habit and backfill `habitId=1`. Remove `fallbackToDestructiveMigration()`; enable `exportSchema` for migration tests. | `AppDatabase.kt`, `Habit.kt`, `CheckIn.kt`, `CashOut.kt` |
| 2 | **Migrate per-habit prefs** | One-time, idempotent startup migration: copy DataStore `exerciseReward`, `availableFreezes`, `totalFreezeSpending`, `frozenDates` onto the default habit row + `frozen_days`. Slim `PreferencesRepository` to app-wide settings + PIN. | `PreferencesRepository.kt`, `OinkApplication.kt` |
| 3 | **Scope logic to a habit** | Thread `habitId` through `CheckInRepository` & `CashOutRepository` (queries, streak, halving, freeze, recalc). `BalanceCalculator` stays pure. Update DAOs to filter by habit. Fix existing tests + fakes. | `CheckInRepository.kt`, `CashOutRepository.kt`, `*Dao.kt`, `src/test/…` |
| 4 | **Home list + detail + add** | New `HabitListViewModel` + habit-card list as Home. Turn today's `HomeScreen` into a `HabitDetail` screen keyed by `habitId`. New Add-habit screen + `AddHabitViewModel`. Route wiring. | `HomeScreen.kt`, `Navigation.kt`, `MainViewModel.kt` |
| 5 | **Private gate** | Add `isPrivate` handling, the always-on Private tile, PIN create/verify (salted hash), unlock screen, private list, ephemeral unlock state. Exclude private habits from list & widget. | `PrivateViewModel.kt`, `Navigation.kt`, `PreferencesRepository.kt` |
| 6 | **Widget, per-habit screens, verify** | Per-instance widget config activity (pick a non-private habit per widget; render that habit's balance/streak). Scope Calendar/History/Rewards to `habitId`. New tests (habit repo, PIN hashing, migration). End-to-end run on the emulator. | `OinkWidget.kt`, `CalendarScreen.kt`, `HistoryScreen.kt`, `RewardsScreen.kt` |

## Risks & migration

- **Data loss on upgrade.** The DB ships `fallbackToDestructiveMigration()` — any schema bump wipes real data. Phase 1 must replace it with a real, tested `MIGRATION_1_2`.
- **DataStore ↔ Room split-brain.** Per-habit prefs live in DataStore but the new home is Room. The Phase 2 startup migration must be idempotent and run once, or freeze counts / reward could double-apply or reset.
- **Widget leakage.** The Glance widget must never surface a private habit; the per-instance picker excludes private habits.
- **Streak recalculation cost.** `recalculateBalancesAfter` is O(n) per habit; keep queries habit-scoped so one big habit doesn't slow others; bulk ops now loop per habit.
- **Cash-out balance formula.** Balance = checkIns − cashOuts − freezeSpending must now be computed per habit. Any query missing the `habitId` filter silently mixes banks together.

## Decisions

Resolved:

- **PIN security** → salted hash + plaintext rows (casual-snooping threat model accepted).
- **Widget** → one configurable widget per habit; each instance picks a non-private habit.

Still open (default to the first if unspecified when work resumes):

- **Reminders** → keep one global daily reminder for now (recommended), or per-habit reminder times.
- **Existing workout data** → migrate into a default "Workout" habit preserving everything (recommended), or fresh start.
