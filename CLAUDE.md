# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

LP Table Look — Android tablet app that visualizes restaurant tables and pulls table/order data from a POS ("Kasse") server over a raw TCP socket. Single Gradle module (`:app`), package `com.lotus.lptablelook`.

- Kotlin, **View-based UI (XML layouts + custom `View`)** — no Jetpack Compose in this project.
- minSdk 30, targetSdk/compileSdk 36, Java 17, AGP 9.x / Gradle 9.6, KSP for Room.
- Both activities are locked to `landscape` (tablet target).

## Commands

```bash
./gradlew assembleDebug            # build debug APK
./gradlew installDebug             # build + install on connected device
./gradlew build                    # full build incl. lint + unit tests
./gradlew test                     # JVM unit tests
./gradlew testDebugUnitTest --tests "com.lotus.lptablelook.ExampleUnitTest"   # single unit test class
./gradlew connectedAndroidTest     # instrumentation tests (device/emulator required)
./gradlew lint                     # Android Lint -> app/build/reports/lint-results-debug.html
./gradlew clean
```

On Windows use `gradlew.bat`. No ktlint/detekt configured. Tests are still the template stubs — there is no real test suite yet.

## Architecture

**Dependency wiring** is manual through `TableLookApp` (the `Application`): `AppDatabase` → `TableRepository` → `SyncService`, all `by lazy`. No DI framework. Activities reach them via `(application as TableLookApp).repository` / `.syncService`.

**Data layer** (`data/`): Room v10, DB name `lptablelook_database`, `fallbackToDestructiveMigration(dropAllTables = true)` — **schema changes wipe local data instead of migrating**; bump `version` and expect a wipe, or add real migrations if data must survive. Entities `Table`, `Platform`, `Settings` live in `model/`. `Settings` is a single-row table (`id = 1`) holding server IP, port, and every UI toggle (background style, chairs, price badges, currency symbol, default table scale). DAOs expose `Flow` for observation plus `...Sync()` suspend variants for one-shot reads inside sync logic.

**Network layer** (`network/`) — the part that needs the most care:
- `SocketService` opens a **new `Socket` per request** (mirrors the original Java client), UTF-8, writes one line, reads until the `;SON;` terminator. Connect timeout 30s, read timeout 10s. Never reuse/pool it.
- Wire protocol is delimiter-based text, not JSON. Constants in `SocketService`: `DATA_SEPARATOR = ";!;"`, `DATA_FINISH = ";SON;"` (record terminator), `EMPTY = ";BOS;"`, `ERROR = ";ERROR;"`, `RETURN_OK/RETURN_FAULT = "True"/"False"`.
- `ServerCommand` enum holds the numeric opcodes and formats them zero-padded to 2 digits: `CMD_GET_PLATFORMS(22)`, `CMD_GET_ALL_TABLES(39)`, `CMD_GET_FULL_TABLES(28)`, `CMD_GET_TABLE_ORDERS(32)`, `CMD_GET_TABLE_SUM(27)`, `CMD_TABLE_STATUS(40)`, `CMD_REGISTER_PHONE(56)`. Note the README's command table is stale — the enum is the source of truth.
- Requests carry a device id (`DeviceUtils.getDeviceId`) that the server uses for registration/authorization.
- `SyncService` orchestrates: `syncAll()` = platforms (22) → all tables (39) → per-platform status (28), then sums per table (27). Every entry point checks `NetworkUtils.isWifiConnected` first and returns the `SyncResult` sealed class (`Success` / `Error(msg)` / `NoConnection`). Parsing is `split(DATA_FINISH)` into records, then `split(DATA_SEPARATOR)` into fields — always guard field count, the server pads/varies.
- Port is fixed: `FIXED_PORT = 1453` in `model/Settings.kt`.

**UI layer**: `MainActivity` (floor view, platform tabs built programmatically, WiFi/battery monitors via `ConnectivityManager.NetworkCallback` + `ACTION_BATTERY_CHANGED` receiver, fullscreen immersive) and `SettingsActivity` (server IP, connection test, sync, floor-plan image, display toggles). All async work runs in `lifecycleScope.launch`; repository/network functions are `suspend` and switch to `Dispatchers.IO` internally.

`view/TableFloorView.kt` is the heart of the app: a custom `Canvas` view drawing floor background, tables (oval/rect), chairs (5 styles), price badges, grid, and handling touch — tap, 500 ms long-press, and drag with grid snapping in edit mode. It owns its own `Paint` objects and a scaled background `Bitmap`; it communicates upward only via the `onTableClicked` / `onTableLongClicked` / `onTablePositionChanged` lambdas. Position changes are persisted by the activity through `repository.updateTablePosition`. At 680 lines it is the largest file — extend drawing helpers rather than inlining more into `onDraw`.

Dialogs in `ui/` (`EditTableDialog`, `TableDetailsDialog`, `TableOrdersDialog`, `WifiInfoDialog`, `BatteryInfoDialog`, `ProgressDialog`) are plain classes over `Dialog`/`AlertDialog`. User-facing feedback goes through `PopupMessage.success/warning/error(...)` — not `Toast`.

## Conventions

- **All user-facing strings are localized**: `values/` (base), `values-de`, `values-en`, `values-es`, `values-fr`, `values-tr`, plus `values-night` and `values-port`. Adding a string means adding it to every locale folder; never hardcode UI text.
- README and in-code German strings are intentional — the product language is German; commit messages in this repo are Turkish.
- Debug builds ship LeakCanary; keep it `debugImplementation` only.
- Dependencies go through the version catalog `gradle/libs.versions.toml`, never inline coordinates.
- Release build currently has `isMinifyEnabled = false` and no signing config — releases are built from Android Studio.
