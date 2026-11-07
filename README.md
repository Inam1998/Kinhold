# Kinhold

A private family tree for Android, built to keep your own tree without a subscription. Native Kotlin and Jetpack Compose. Your tree stays on the phone: the app asks for no internet permission today.

## What it does
- A focus-person tree you can pan, zoom and tap, with several spouses, siblings, half and step relatives, and adopted, foster and step parent links.
- Quick add (just a name), full person forms with dates (including Hijri), places, notes, events, sources, photos, contact details and your own facts.
- Backups: manual files, automatic copies, a safety copy before anything big, GEDCOM import and export.
- Sharing with relatives: send a branch as a file; merge theirs into yours with a choice on every difference.
- Relationship finder with Urdu and Punjabi terms, upcoming birthdays and anniversaries with an optional reminder, tree checks, duplicate joining, and a PDF or picture chart.
- Optional app lock, light and dark themes, and an in-app Help guide with a page for every feature and its limits.

Ads and a one-time Pro unlock are planned but not built.

## Docs
- Project notes, rules and the current status of every feature: [CLAUDE.md](CLAUDE.md)
- Plan, research and monetization: [docs/PLAN.md](docs/PLAN.md)
- A test GEDCOM file with events: [docs/test-family-events.ged](docs/test-family-events.ged)

## Build

```bash
cd android
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Open the `android/` folder (not the repo root) in Android Studio. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

The on-phone tests (`./gradlew :app:connectedDebugAndroidTest`) uninstall the app and delete the tree on the phone, so save a backup first (see CLAUDE.md).
