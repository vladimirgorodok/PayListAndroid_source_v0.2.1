# PayList — Android project v0.2.0

PayList is an Android payment tracker built with Kotlin, Jetpack Compose, Material 3, Room and WorkManager.

## Implemented in this source revision

- One-time and monthly payment templates.
- Partial and full payments, with money stored as integer minor units.
- Monthly recurrence advances only after the current period is fully paid.
- Independent monthly-series cutoff date and total amount limit.
- The final occurrence is reduced to the remaining amount limit.
- Room transactions wrap payment recording, recurrence creation, operation editing/deletion and backup restore.
- Unique `(templateId, periodKey)` index prevents duplicate monthly occurrences.
- Operation history is shown in the payment detail and supports editing/deletion.
- Deleting or reducing an operation removes future unpaid generated periods and recalculates the current balance. Editing an old period is blocked if later periods have already been paid.
- Month navigation, status filters and paid-payment history.
- Logo search opens an image search in the browser; local images can be selected with Android's document picker and shown on payment cards.
- CSV import/export, XLSX import/export (minimal OOXML workbook), and ZIP backup/restore.
- Daily WorkManager reminders for unpaid payments due today through the next three days.

## Open/build

1. Open `PayListAndroid` in Android Studio.
2. Use JDK 17 or newer and Android SDK Platform 35.
3. Allow Android Studio to sync the Gradle project and download dependencies.
4. Run the `app` configuration on an Android 8.0+ device (API 26+).

The source archive does not include the Gradle Wrapper. Android Studio must configure a compatible Gradle installation for the project (Android Gradle Plugin 8.9.2).

## Important verification note

This revision has been updated at source level, but it has **not been compiled or run in an Android SDK environment here**. No APK is included. Room/Compose integration, SAF import/export, notification permission behavior, and device-specific edge cases still require a Gradle build and on-device QA before this can be called a release build.

## Data and backup

- Currency amounts are stored as integer minor units.
- CSV and XLSX use a row type column (`T` template, `O` occurrence, `P` payment operation) to preserve IDs and payment history.
- ZIP backup contains `paylist.csv`.
- Import replaces matching database IDs. Create a backup before importing into an existing database.

## v0.2.1 source audit corrections

- Prevents edits/deletions in an earlier period when any later period already contains payment operations, not only when a later period is fully paid.
- Future generated unpaid periods are deleted only when they contain no payment operations; this avoids cascading deletion of partial-payment history.
- ViewModel now acquires its busy guard synchronously before launching work, preventing rapid repeated taps from queuing duplicate actions.
- Amount conversion uses decimal arithmetic and HALF_UP rounding to minor currency units.
- Payment creation validates recurrence type and a three-letter currency code.
- Export reads templates, occurrences and operations in one Room transaction for a consistent backup snapshot.
- KSP is configured to export Room schemas into `app/schemas`.

## Audit status

The source archive has been statically reviewed and the above source corrections have been applied. A full Android build has **not** been run: this environment does not have a Gradle executable, Android SDK, or `ANDROID_HOME`/`ANDROID_SDK_ROOT`. The standalone Kotlin compiler is present, but AndroidX/Compose/Room dependencies are not on its classpath, so it cannot validate Android compilation. The XLSX reader is intended for the PayList workbook layout and first worksheet; arbitrary third-party spreadsheets with unrelated columns or shared-string layouts are not guaranteed to import. Do not treat this source revision as a verified APK or production release.
