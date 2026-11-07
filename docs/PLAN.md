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
- Storage is not built yet. Plan: a local database with atomic writes, a full-file backup, and GEDCOM as the exchange format.

## Risks
- The tree view with large, tangled real families (multiple marriages, cousins who marry) is the hardest engineering part.
- Suggestions from scans and voice will be wrong sometimes; confirming must be fast.
- Many people need a tree only occasionally, so ad income depends on the daily hooks and on new users arriving.
- Contacts, microphone and camera permissions need honest store disclosure.
- Name and trademark are unchecked beyond Play search.
