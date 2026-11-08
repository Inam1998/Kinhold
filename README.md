# Kinhold

A private family tree for Android, built to keep your own tree without a subscription. Native Kotlin and Jetpack Compose. Your tree stays on the phone: the app asks for no internet permission (only the optional morning reminder asks to show notifications).

Version 0.1.0, not yet released. Database version 9, backup format version 9.

## What it does
- A focus-person tree you can pan, zoom and tap, with several spouses, siblings, half and step relatives, and adopted, foster and step parent links. Tree cards, the focus card and Find work on big and small phones, large text and landscape.
- Quick add (just a name), full person forms with a calendar for dates (Gregorian and Hijri, including "about, before, after, between"), a country picker for phone numbers, a map pin from an address or a Plus Code, places with suggestions from places already used, notes, events, sources, photos, contact details and your own facts. Link two people who are already in the tree, and unlink them again.
- Find by name, place or notes; a life timeline on each person; a statistics screen; checks for impossible dates, duplicates and separate groups of people not linked to the main tree; an "On this day" line for birthdays and remembrance days.
- Backups: manual files, automatic copies, a safety copy before anything big, GEDCOM import and export, and sending a copy through the phone's share sheet.
- Sharing with relatives: send part of your tree as a file (WhatsApp, email or any app) and open a relative's file with a choice on every difference, a printable family group sheet (PDF), a one-page web page for relatives without Kinhold, and a PDF or picture chart.
- Relationship finder with Urdu and Punjabi terms, upcoming birthdays and anniversaries with an optional reminder, optional app lock, light and dark themes, and an in-app Help guide with a page for every feature and its limits.

Ads and a one-time Pro unlock are planned: the Pro screen and ad placeholders exist in debug builds only, and there is no ad service, billing or internet permission yet.

## Docs
- Project notes, rules, the status of every feature, test counts and what is left: [CLAUDE.md](CLAUDE.md) (start with its last section, "Where things stand")
- Plan, research and monetization: [docs/PLAN.md](docs/PLAN.md)
- Test GEDCOM files: [docs/test-family-events.ged](docs/test-family-events.ged) (events, a divorce) and [docs/test-family-ancestry-style.ged](docs/test-family-ancestry-style.ged) (a hand-written file in the style Ancestry and MyHeritage produce; not a real export)
- Phone helper scripts: [scripts/phone-backup.sh](scripts/phone-backup.sh) and [scripts/phone-restore.sh](scripts/phone-restore.sh) copy the app's tree and preferences off the phone and back

## Build

```bash
cd android
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Open the `android/` folder (not the repo root) in Android Studio. The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`; copies for the owner go in `D:\Android App Releases\Kinhold\Kinhold-<version>.apk` when asked.

The on-phone tests (`./gradlew :app:connectedDebugAndroidTest`) uninstall the app and delete the tree on the phone, so run `scripts/phone-backup.sh` first, then `adb install -r` the APK and run `scripts/phone-restore.sh` afterwards (see CLAUDE.md).
