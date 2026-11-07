# Kinhold plan

Written 2026-11-07 from market research on Google Play. Treat the numbers as a snapshot, not a forecast. Nothing here promises sales or Play approval.

## Why this app
- Category scans found few weak leaders; the best gap was genealogy. Big apps are 10M+ downloads each: Ancestry 4.0 (195K reviews), MyHeritage 3.8 (258K), FamilySearch Tree 3.5 (53.7K).
- Complaints read on Play: a tree wiped after an update (8 generations lost), a $25 early-cancellation fee, constant upsells, no support, price, and tree navigation that stopped responding after an update.
- Small apps are basic: FamilyGTG 4.9 (100K+, simple tree, GEDCOM, photos), Family Gem 4.5 (100K+, open source).
- Not covered by our app, on purpose: a historical records database, DNA, hints from other people's trees. Users are people who already know their family and want to keep it.
- Naming: "Kinhold" had no exact match on Play search on 2026-11-07. Still to check by the owner: USPTO trademark search, domain, social handles.

## Feature list
**Core:** smooth tree for 1,000+ people; people with dates, places, notes, photos and documents; multiple marriages and step relations later; GEDCOM import/export; duplicate detection and merge; warnings for impossible data; Hijri and Gregorian dates; names in several scripts including Urdu; relationship finder; local-language kinship terms (chacha, mamu, phupho, khala and so on).
**Automation (always as suggestions the user confirms):** scan a certificate or old photo to propose a person (on-device text recognition); record an elder, transcribe, and propose names, places and dates; import living relatives from phone contacts (birthdays, relations, photos).
**Not possible or not wanted:** pulling family from Facebook, Instagram or WhatsApp (no sanctioned API; Facebook removed relationship permissions in 2018); FamilySearch API (closed, case-by-case approval); face recognition (version 1).
**Public data later:** WikiTree read-only API for public profiles (needs internet, must link back); Wikidata for notable people.
**Output:** printable wall chart, family book PDF, story pages.
**Daily hooks:** "on this day" birthdays and anniversaries, a daily family-story prompt, home-screen widgets.

## Money
1. Ads: banner or native on list screens only; interstitial rarely, after a successful export; optional rewarded ad to unlock one export. Never over the tree, while editing or recording. Needs a consent prompt in some regions and an honest Data safety form.
2. One-time Pro: removes ads and unlocks print chart, family book, unlimited stories and scan-to-person. No cap on the number of people in the free tree.
3. Later: printed products through a partner; an optional paid cloud backup and sharing between relatives (has server cost, so only if people ask).
Revenue depends on active users and the rate advertisers pay; measure with real ad reports before planning around it.

## Architecture notes
- Native Kotlin and Jetpack Compose, minSdk 26, targetSdk 36. The tree is drawn on a single Canvas: only boxes on screen are drawn, detail drops at low zoom, text is measured once in tree units and drawn on the scaled canvas so zooming never re-measures.
- Layout (`tree/TreeLayout.kt`) is pure Kotlin with no Android dependencies, so it is unit tested. Edges are stored in one flat float array.
- Storage (built): SQLite, one transaction per change, an immutable `Family` snapshot published to the UI; photos in their own table (database version 2, with a tested upgrade step); a one-file backup (JSON, format version 2, photos as Base64, version 1 files still read) with a safety copy written before every restore or import; GEDCOM 5.5.1 export and a forgiving 5.5 / 5.5.1 / 7.0 reader.
- Relatives that are not drawn (siblings, half-siblings, step relatives) are worked out from the stored parent and spouse links (`model/Relatives.kt`), not stored.
- Phone-only choices (theme, accent) live in a preferences file, outside the tree and its backups.
- Events and sources (0.1.0): birth and death keep their year fields (`born`, `died`), and the optional month and day sit beside them (`birth_month`, `birth_day`, `death_month`, `death_day`); every other event is a row in an `event` table that belongs to one person or to one couple (a marriage, divorce, engagement, annulment). A `source` row is a plain-text citation for one event or for a person in general. The tree snapshot (`Family`) holds only a small `CoupleInfo` per couple (earliest marriage date, ended or not); events themselves are read from the database for one person at a time, so memory and main-thread work do not grow with the number of events.

## Progress (2026-11-07, version 0.1.0)
Built: focus-person tree view with several spouses, local storage with add/edit/delete, backup, restore and undo, GEDCOM import and export, in-app Help guide, photos with default avatars, new look, settings (theme and accent), siblings and step relatives, events (with full dates, marriages and divorces) and plain-text sources. Details and limits are in `CLAUDE.md`.
Events and sources are built (0.1.0, see `CLAUDE.md`). Source scans or documents still come later because they make backups much larger; an "about/between" date type, event places on a map, and events shown on the tree cards are not built.
After that: recorded adopted/foster/step link types and more than two parents; ads and Pro; scan-to-person; voice stories; contact import.

## Risks
- The tree view with large, tangled real families (multiple marriages, cousins who marry) is the hardest engineering part.
- Suggestions from scans and voice will be wrong sometimes; confirming must be fast.
- Many people need a tree only occasionally, so ad income depends on the daily hooks and on new users arriving.
- Contacts, microphone and camera permissions need honest store disclosure.
- Name and trademark are unchecked beyond Play search.
