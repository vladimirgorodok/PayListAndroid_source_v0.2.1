# PayList v0.2.1 — technical audit

## Project structure

- Android application module: `app`
- UI: Jetpack Compose + Material 3
- State: ViewModel + Kotlin Flow
- Persistence: Room database with three entities (templates, occurrences, operations)
- Background reminders: WorkManager
- Transfer: CSV, minimal OOXML XLSX, ZIP containing CSV

## Corrections applied in v0.2.1

1. Prevented deleting future periods that contain payment operations.
2. Prevented editing/deleting an earlier period if a later period already has any operations, preserving chronology and audit history.
3. Added a synchronous ViewModel busy guard to stop rapid repeated actions from being queued.
4. Switched amount conversion to `BigDecimal` with HALF_UP rounding.
5. Added validation for recurrence values and currency code format.
6. Made exports read all three tables inside a single Room transaction.
7. Configured Room schema export through KSP.

## Findings / limitations still requiring work

- No Gradle Wrapper is included.
- No Gradle executable or Android SDK is installed in the inspection environment; Gradle Sync, KSP, APK compilation and emulator/device tests cannot be run here.
- Room schema generation has been configured but the generated schema has not yet been produced or checked in a real Gradle build.
- XLSX import is a minimal reader for the PayList workbook layout and first worksheet, not a general-purpose spreadsheet importer.
- CSV/XLSX/ZIP round-trip, foreign-key restore behavior, WorkManager notification behavior, SAF permissions, and Android 13+ notification permission need device testing.
- Current month navigation displays one month at a time; the requested simultaneous current/next-month overview remains a UI enhancement.
- A signed release APK and signing configuration have not been created.

## Build requirements

- JDK 17+
- Android SDK Platform 35
- Android Gradle Plugin 8.9.2 compatible Gradle distribution (8.11.1+)
- Network access during first sync to resolve Maven dependencies

## Status

Source-level corrections only. Not a compiled or verified release.
