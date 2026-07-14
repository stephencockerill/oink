# Supporting "quit" (negative) habits

Status: **design spike, not started** (design research per issue #67; no implementation).
Companion reading: [`habit-psychology.md`](habit-psychology.md) (the behavioral principles this grounds in) and [`multi-habit-plan.md`](multi-habit-plan.md) (the model this extends).

## Problem

Some habits are about *stopping* a behavior ("no cigarettes", "no doomscrolling") rather than *doing* one.
The current model assumes a positive "did you do it?" action, which makes negative habits awkward in three ways:

1. **Timing.** For a "don't do X" habit you cannot truthfully confirm success until the day is over, so the user either loses the streak or preemptively marks a day before it has actually passed.
2. **Framing.** "Did you work out today?" and "time to do your habit!" read backwards for a behavior you are trying to avoid.
3. **Reward semantics.** For a build habit, *doing it* earns money; for a quit habit *abstaining* is the win, so it is unclear what triggers the reward and when.

## Core idea: invert the default outcome

The whole design follows from one move.

For a **build** habit, passivity means failure: if the user does nothing, they did not act and did not earn.
For a **quit** habit, passivity means *success*: getting through the day without the behavior is the win, and it is a non-event that cannot be observed until the day is over.

So flip which outcome is the passive default, and make the affirmative action the *slip report* rather than the success report.

| | Build (today) | Quit (new) |
| --- | --- | --- |
| Affirmative action | "I did it" earns the reward | "I slipped" fires the penalty |
| Passive outcome | a miss (streak breaks) | a **clean day** (earns the reward) |
| When the reward settles | instantly, on tap | at **day-close** (auto-resolve at local midnight) |
| When the penalty fires | on a logged or absent miss | the moment a slip is logged |

This directly resolves the three problems:

- **Timing** is resolved by never asking the user to confirm a non-event mid-day.
The day closes itself clean at midnight; the user only ever acts to *report a slip*.
- **Framing** is resolved because the card and notifications stop telling the user to "do" anything.
The primary control becomes a destructive-styled **"I slipped"**; success is silent.
- **Reward semantics** are resolved because a clean day auto-earns `rewardValue` at day-close, and a slip is the halving event.

## Why this is a strong psychological fit

Grounded in [`habit-psychology.md`](habit-psychology.md):

- **Loss aversion (Kahneman & Tversky).**
Quitting is inherently loss-framed.
The balance banked across N clean days becomes the stake that a slip halves.
Daily accrual is not "too easy": it is precisely what gives the halving something to hurt.
The halving engine fits quit habits at least as well as build habits.
- **Positive reinforcement over punishment (Finch).**
"Clean by default" makes the app's resting state "you are winning" rather than "you owe a check-in", which avoids the daily nag and the guilt spiral.
- **Recovery mechanisms.**
A freeze becomes *slip forgiveness*: one cigarette should not nuke a 40-day quit streak.
This honors the doc's rule that one miss must never destroy everything.
- **Ironic-process caution (added for cessation, not in the doc).**
A midday "don't smoke!" reminder is a *cue* that can trigger the very craving it warns against.
Quit-habit notifications must therefore be pride and encouragement framed around the streak, delivered at safe moments, never a "go avoid it" nudge.
This is the single largest copy departure from build habits.

## Check-in and timing flow

A quit habit's day has three states: **clean-so-far** (in progress), **clean** (resolved success), and **slipped**.

1. **During the day** the habit is *clean-so-far* by default.
The user does nothing to stay clean.
The one affirmative control is **"I slipped"**, which logs a slip for the current day and immediately fires the penalty.
There is no "still clean" tap: a good day is fully passive.
2. **Mid-morning** a celebratory notification fires ("Day 12 clean, keep it going 🐷"), framed entirely around the streak already earned rather than the behavior being avoided.
This gives the in-the-moment reinforcement that a passive win would otherwise lack, without cueing the craving.
3. **At local midnight** a day-close job auto-resolves the just-ended day.
If no slip was logged, it writes a clean check-in and accrues `rewardValue`; the pig celebrates the banked day.
If a slip was logged, the day is already resolved and the job does nothing for it.
4. **Retroactive correction** ("I actually slipped yesterday") reuses the existing past-edit path.
Editing a resolved clean day to a slip re-halves from that day and recalculates forward via `recalculateBalancesAfter`, so auto-resolve is an assumption the user can always correct, never an irreversible lock.
This preserves honesty: the app never claims a success the user can't undo.

Grace until midnight falls out for free: a slip at 11pm lands on that day, and a slip discovered the next morning is a one-tap past edit.

## Reward and penalty mapping

The arithmetic in [`CheckInRepository`](../app/src/main/java/com/oink/app/data/CheckInRepository.kt) does not change.
Only the *meaning* of the check-in and the *default resolution of an unlogged day* change.

- A `CheckIn` row's `didExercise` flag is reinterpreted per habit type: for a quit habit `true` means "stayed clean" and `false` means "slipped".
A quit slip is arithmetically identical to a build miss, so the same `calculateNewBalance` halving applies unchanged.
- **Streak** is consecutive clean days; `calculateStreak` needs no change beyond the reinterpretation above.
- **Halving (÷2)** is the slip event.
- **Freezes** gain a second meaning for quit habits, *slip forgiveness*: after a logged slip the user may spend a freeze so the slip does not break the streak.
Per the decision below, forgiveness protects the streak; whether it also reverses the halving is called out as an open question.
- The only genuinely new behavior is the **default resolution of an unlogged past day**: a build gap stays broken, a quit gap resolves to a clean success that accrues the reward.

## Copy and notification changes

All copy branches on `habitType`.

| Surface | Build | Quit |
| --- | --- | --- |
| Card prompt | "Did you work out?" | "Staying clean" with a clean-day count; primary action is **"I slipped"** (destructive style), success is silent |
| Detail page | "balance", "streak" | "protected balance", "clean days" |
| Daily notification | "Time to earn your $5, work out!" (a call to act) | mid-morning **celebration** of the current clean streak; never a reminder about the behavior |
| Slip feedback | n/a | empathetic, not shaming: "Slips happen. Spend a freeze to protect your streak?" per the recovery principle |

## Minimal data-model change

1. **Add `habitType`** to [`Habit`](../app/src/main/java/com/oink/app/data/Habit.kt): an enum `HabitType { BUILD, QUIT }`, defaulting to `BUILD`, stored as a `String` column with a Room `TypeConverter`.
The database is currently v4 (schemas committed under `app/schemas/`), so this ships **`MIGRATION_4_5`** adding the column with SQL default `'BUILD'`, plus a `MigrationTestHelper` migration test.
Every existing habit backfills to `BUILD`, so the change is zero-risk for current users.
2. **Reuse `check_ins` unchanged.**
The `didExercise` column keeps its shape and is reinterpreted per habit type.
Renaming it to `didSucceed` now that the app is multi-habit and no longer workout-only is worthwhile but is deliberately deferred to a separate cleanup ticket (#71) to keep this migration small.
3. **Add a day-close auto-resolve worker.**
Because balance is stored as `balanceAfter` per row, clean days must be materialized to accrue money; they cannot be inferred lazily without rewriting the ledger model.
Model it on the existing self-rescheduling one-time-work pattern in [`ReminderScheduler`](../app/src/main/java/com/oink/app/notifications/ReminderScheduler.kt) / [`ReminderWorker`](../app/src/main/java/com/oink/app/notifications/ReminderWorker.kt): a worker anchored to the next local midnight that, for each `QUIT` habit with no check-in on the just-ended day, inserts a clean `CheckIn`, then reschedules itself.

### Note on habit-scoped queries

The v3→v4 migration landed the multi-habit schema but did not thread `habitId` through `CheckInDao` or `CheckInRepository`, so every check-in query still reads across all habits (tracked in #73).
`ReminderWorker` calling `getCheckInForDate(date)` with no habit key is one instance of that gap.
Quit habits and the day-close auto-resolve worker are only meaningful once the check-in read/write path is habit-scoped, so **#73 is a prerequisite for implementing this design**.
Do not add new date-only (unscoped) check-in queries here.

## Open questions for the implementation ticket

1. **Does slip forgiveness also reverse the halving, or only protect the streak?**
Protecting only the streak is simpler and keeps loss aversion intact (you still feel the ÷2); reversing the halving too is gentler but weakens the stake.
Recommendation: freeze protects the streak only; the halving stands.
2. **Should the mid-morning celebration reuse the single global reminder time, or get its own quit-specific schedule?**
Recommendation: reuse the existing reminder infrastructure with a fixed mid-morning default for quit habits, revisited if per-habit reminder times land.
3. **`didExercise` rename.**
Filed as #71; sequence its version bump against `MIGRATION_4_5` so migrations stay linear.

## Proposed implementation ticket breakdown

Ordered so the app stays buildable at each step.

| # | Ticket | Scope |
| --- | --- | --- |
| 1 | **`habitType` enum + migration** | Add `HabitType` and the column to `Habit` with a converter; `MIGRATION_4_5` with SQL default `'BUILD'`; migration test; bump to v5 with the new schema JSON committed. |
| 2 | **Reinterpret check-in semantics per type** | Thread `habitType` into the check-in and streak read paths so `didExercise` reads as clean/slip for quit habits; habit-scope the check-in DAO queries this touches. Repo/VM tests with fakes. |
| 3 | **Day-close auto-resolve worker** | Self-rescheduling midnight worker that materializes clean days for quit habits and accrues the reward; unit-tested with a fixed `Clock` and fakes. |
| 4 | **Slip flow + freeze forgiveness** | "I slipped" action wiring, the halving on slip (existing math), and spend-a-freeze-to-forgive with empathetic recovery copy. |
| 5 | **Copy, notifications, and quit UI states** | Type-branched card prompt, detail labels, and the mid-morning celebratory notification; add-habit flow gains the build/quit choice. |
| 6 | **End-to-end verification** | Run both a build and a quit habit through a full cycle on the emulator across a simulated midnight rollover, including retroactive slip correction. |
