# Android conventions

> **Consult the `android-expert` agent for any non-trivial Android work.**
> Whenever you write, change, or review Compose UI, ViewModels/state, coroutines & Flow, Room, DataStore, WorkManager, Glance widgets, Navigation, or Gradle/manifest config, hand it to the `android-expert` subagent (`.claude/agents/android-expert.md`). It carries the authoritative, cited canon and this repo's specifics. This doc is the quick reference; the agent is the authority.

Oink is a single-module app (`com.oink.app`) with **manual DI** (no Hilt) via `OinkApplication` + provider objects. Stack: Kotlin 2.0 / Compose BOM 2024.11 / Material3, Room + KSP, DataStore Preferences, WorkManager, Glance widgets, Navigation Compose. Pattern is **MVVM + Repository** with `StateFlow`-backed UI state. `minSdk 26`, `compileSdk`/`targetSdk 35`, Java/JVM 17. Tests are JUnit4 + Robolectric + coroutines-test with hand-written fakes for DAOs/repos (`app/src/test/.../Fake*.kt`). Match these idioms; DI is manual providers, not annotations, unless a change explicitly introduces Hilt.

## Code style

- Kotlin idioms: `data class`, `sealed interface`, `when` expressions, extension functions. Prefer immutability (`val`, immutable collections) and functional patterns.
- Coroutines for async work, never callbacks. `StateFlow` over `LiveData` for new code.
- Meaningful names: `userRepository` not `repo`, `isLoading` not `loading`.
- **Absolute imports**, no wildcard imports.
- Null-safety honestly: no `!!` to silence the compiler; prefer `?.`/`?:`/`requireNotNull` with a message. Exhaustive `when` over `sealed`/`enum` - no `else` catch-all that hides new cases.

## Architecture & state

- Three layers: UI (Compose + state holder) → Domain (optional) → Data (repository owns the source of truth). ViewModels don't touch DAOs/DataStore directly - route through `*Repository`.
- State is hoisted (UDF): state flows down, events flow up as function calls / `sealed interface` intents. Don't pass a `ViewModel` down into child composables; pass the state it needs and lambdas.
- UI state is a single immutable `data class`, not scattered `mutableStateOf`s. One-shot events (navigation, snackbars) are consumable events / `Channel` / `SharedFlow`, not lingering state flags.
- `remember` for transient derived values; `rememberSaveable` for anything that must survive config change/process death that isn't in the ViewModel. Screen state belongs in the ViewModel.
- Manual DI: construct dependencies in `OinkApplication` / provider objects and pass them in. Keep providers testable (swap fakes). No hidden singletons/global `object` state.

## Compose & recomposition

- Read state as late as possible - defer reads into lambdas (`Modifier.offset { }`, `graphicsLayer { }`) so only layout/draw re-runs, not composition. Use `derivedStateOf` for threshold-based derived values. Never write to state already read in the same composition.
- Stability: `List`/`Set`/`Map`/`var`-holding classes are unstable and defeat skipping. Use `kotlinx.collections.immutable` or `@Immutable`/`@Stable`, and immutable `data class` UI state with `val`s.
- `LazyColumn`/`LazyRow`/`LazyVerticalGrid`: always provide a stable `key` from data (not index), and `contentType` for heterogeneous lists. Never nest scrollables of the same axis.
- Side effects go through the right primitive, never inline in composition: `LaunchedEffect(key)`, `rememberCoroutineScope`, `DisposableEffect`, `SideEffect`, `produceState`/`snapshotFlow`. A wrong/missing key is a bug.
- No heavy work (sorting, filtering, allocation, I/O) in composition - hoist it. `Modifier` order is semantic.

## Coroutines & Flow

- Collect Flows lifecycle-aware: `collectAsStateWithLifecycle()` in Compose, not bare `collectAsState()` for app data.
- Expose `StateFlow`/`SharedFlow` from ViewModels via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`. Back a public flow with a private `_state` + `asStateFlow()`; don't expose `MutableStateFlow`.
- Launch in the right scope (`viewModelScope`), never `GlobalScope`. Structured concurrency - a failing child must not orphan siblings.
- Suspend functions are main-safe: do `withContext(Dispatchers.IO/Default)` at the lowest level, don't push dispatcher choice onto callers. Room/DataStore already suspend main-safely - don't wrap them in redundant `withContext(IO)`.
- Cancellation is cooperative: never swallow `CancellationException` (rethrow it); clean up in `finally`.

## Room / persistence

- DAO queries return `Flow<T>` for observation and `suspend` for one-shots. Never call blocking DAO methods on the main thread. Multi-statement writes go in `@Transaction`.
- **Money is stored as integer minor units (`Long` cents), never `Double`/`Float`.**
- Every schema change needs a migration. `exportSchema=true` and the schema JSON under `app/schemas/` are committed; `MigrationTestHelper` tests migrations against them. Never use `fallbackToDestructiveMigration()` outside dev - it wipes user data.
- DataStore Preferences is async/Flow-based - read via `.data.map { it[KEY] }`, write via `edit {}`; never block on it. Keep preference keys centralized and typed. No `SharedPreferences` for new code.

## WorkManager / Glance / background

- WorkManager is for deferrable, guaranteed work (reminders, sync), not exact-time alarms. Use `AlarmManager` only when the domain truly needs exact timing. Make workers idempotent, pass small primitive `inputData`, enforce uniqueness with `enqueueUniqueWork`/`PeriodicWork` + an `ExistingWorkPolicy`, set constraints/backoff.
- Notifications (API 26+): create the `NotificationChannel`; on API 33+ request `POST_NOTIFICATIONS` at runtime or posts silently drop.
- Glance widgets are their own composition - use Glance composables/`GlanceModifier`, drive state via `updateAll`/`GlanceStateDefinition`, keep work off the main thread. Don't share Compose UI code into Glance.

## Manifest / Gradle / security

- Declare only needed permissions; runtime-request the dangerous ones. No secrets/API keys in source, VCS, or the manifest - use `local.properties`/`BuildConfig`.
- The version catalog (`gradle/libs.versions.toml`) is the single place for versions - add deps there, reference via `libs.*`; never hardcode a version in `build.gradle.kts`.
- Enable R8/`isMinifyEnabled` for release and keep `proguard-rules.pro` correct for reflection/Room. `exported="true"` components need an intent filter and deliberate review.

## Testing

- ViewModel/repository tests are JVM unit tests with fakes (`Fake*Dao` / `Fake*Repository`), not instrumented, not real Room, unless integration is the point.
- Use `kotlinx-coroutines-test`: `runTest`, a `TestDispatcher`, `Dispatchers.setMain` for `viewModelScope`. Robolectric for Android-framework deps without a device.
- No hidden real time/dispatcher - inject the clock/dispatcher. Compose UI tests use `createComposeRule` + semantics matchers; add `testTag` only where a semantic matcher can't reach.
