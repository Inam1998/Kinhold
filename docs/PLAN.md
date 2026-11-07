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
- Storage (built): SQLite, one transaction per change, an immutable `Family` snapshot published to the UI; database version 8 with a tested upgrade step for every version (photos, events and sources, permanent person ids, contact details and the "me" mark, email and own facts, extra photos, parent link kinds); a one-file backup (JSON, format version 8, photos as Base64, versions 1 to 7 still read) with a safety copy written before every restore or import; GEDCOM 5.5.1 export and a forgiving 5.5 / 5.5.1 / 7.0 reader.
- Relatives that are not drawn (siblings, half-siblings, step relatives) are worked out from the stored parent and spouse links (`model/Relatives.kt`), not stored.
- Phone-only choices (theme, accent) live in a preferences file, outside the tree and its backups.
- Events and sources (0.1.0): birth and death keep their year fields (`born`, `died`), and the optional month and day sit beside them (`birth_month`, `birth_day`, `death_month`, `death_day`); every other event is a row in an `event` table that belongs to one person or to one couple (a marriage, divorce, engagement, annulment). A `source` row is a plain-text citation for one event or for a person in general. The tree snapshot (`Family`) holds only a small `CoupleInfo` per couple (earliest marriage date, ended or not); events themselves are read from the database for one person at a time, so memory and main-thread work do not grow with the number of events.

## Progress (2026-11-08, version 0.1.0)
Built: focus-person tree view with several spouses, local storage with add/edit/delete, backup, restore and undo, GEDCOM import and export, in-app Help guide, photos with default avatars, new look, settings (theme and accent), siblings and step relatives, events (with full dates, marriages and divorces) and plain-text sources. Details and limits are in `CLAUDE.md`.
Events and sources, permanent person ids, share files and merging are built (0.1.0, see `CLAUDE.md`; not yet run on the phone). Source scans or documents still come later because they make backups much larger; an "about/between" date type, event places on a map, and events shown on the tree cards are not built.
Since then (all in version 0.1.0, details in `CLAUDE.md`): sharing a branch and merging it with per-field choices and an anchor step for the file's main person; contact details, map pin, call, text, WhatsApp and map buttons, "this is me"; email and own facts; automatic backup; optional app lock; upcoming dates with an optional reminder; relationship finder with Urdu and Punjabi terms; tree checks and joining duplicates; PDF and picture chart; camera and several photos; Hijri dates; adopted, foster and step links with up to four parents; welcome tour; quick add; sample data that can be cleared; a save-a-backup-first prompt before a merge, import or restore; round back arrows, equal-width buttons, animations, a tree that glides to the tapped person, and a landscape layout.
Also built since (2026-11-08, all in version 0.1.0, details in `CLAUDE.md`): Pro entitlement and debug-only ad placeholders (no ad service, billing or INTERNET permission yet); linking and unlinking people already in the tree; Find by name, place and notes; a life timeline on the Details page; a Tree statistics screen; a list of separate groups (people not linked to the main tree) on Checks and Statistics; place suggestions from places already used; date qualifiers (about, before, after, between; database and backup version 9); sending a share file, backup or chart through the phone's share sheet; Backup and sharing reorganised by what the person wants to do; GEDCOM fixes found with a hand-written Ancestry-style file (suffix, second names, deceased with no date), still to be tried on a real export; person-with-arrow icons for Parent, Child and Sibling.
Still to do, in order: real ads (an ad service, consent flow, INTERNET permission) and Play Billing for the one-time Pro, with the Privacy page, Pro page, store listing and Data safety form changing together; release plumbing (signed build, shrinking tested, icon, screenshots, listing); a real GEDCOM export from the owner's own tree; hand checks of what is still unchecked (app lock, reminders, folder backup, camera, chart export, Hijri picker, Attach to someone, landscape on other screens, TalkBack and large text); profiling on a slow phone; then scan-to-person, voice stories, contact import, a family group sheet PDF, an HTML viewer file for relatives, and several trees as a possible Pro extra. Source scans stay out until backups can cope with their size.

## Risks
- The tree view with large, tangled real families (multiple marriages, cousins who marry) is the hardest engineering part.
- Suggestions from scans and voice will be wrong sometimes; confirming must be fast.
- Many people need a tree only occasionally, so ad income depends on the daily hooks and on new users arriving.
- Contacts, microphone and camera permissions need honest store disclosure.
- Name and trademark are unchecked beyond Play search.

## Ideas backlog (owner, 2026-11-07). Items 1 to 6 are built (see CLAUDE.md); 7 (map view and radius search) is not, and paid map services are left out on purpose
1. **Address and phone on a person.** Records get a text address and one or more phone numbers. Data change: migration, backup format, GEDCOM (ADDR, PHON) and Help page.
2. **Map pin for an address.** Keep the typed address and, separately, a saved pin (latitude and longitude). An icon opens the place in the phone's map app. Cheapest route: a `geo:` or maps link intent, which needs no API key and no internet permission in Kinhold. Pick the pin by sharing or pasting a location from the maps app.
3. **Call, text and WhatsApp buttons** on the person card or Details page, next to the phone number. Intents only (dial, SMS compose, `wa.me` link): they open the phone's own apps, so no permission and no data leaves Kinhold. WhatsApp only if installed.
4. **Set yourself as the central person** ("This is me") and a **Recentre on me** button (today only "Recenter" on the view and "Oldest"). Needs a stored "me" id: migration, backup and share-file handling.
5. **Icons** on pills and actions (Find, Recenter, Backup, Help, Settings, Edit, Details and the new buttons), following the design system's icon style.
6. **Settings and tools navigation.** The chip row will not scale. Likely a floating bottom nav as in the design system: Tree, Find, Tools (backup, import and export, share), More (settings, help). To be decided with the owner.
7. **Map view of the tree and radius search** ("who in my tree lives or was born within 50 km of here"). Needs places turned into coordinates (a saved pin per address or event place).
**Caveats to settle before 7 and the in-app map:**
- Google Maps is not free or open source. Its SDK and APIs need a key with a billing account, per-use pricing above a free allowance, and Google's terms. It also needs the INTERNET permission, which today's Privacy page and "no internet permission" claim say we do not have, and it would send places in the tree to Google. Open options: OpenStreetMap data with MapLibre or osmdroid (free, but tiles still come over the internet, so also the INTERNET permission, which must change together with the Privacy page, the store listing and the Data safety form), or no in-app map at all and hand off to the phone's maps app.
- Radius search needs coordinates, not text. Geocoding text addresses means an online service or a bundled dataset; pins saved by the user avoid that.
- Contact numbers and addresses are sensitive. The planned share file must let the owner leave them out, and "exclude living people" should cover them.
