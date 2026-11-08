package com.inamibrahim.kinhold.model

/**
 * One topic per feature a person can use. Adding a feature means adding a topic here and a page in [HelpGuide]; a test
 * fails if a topic has no page, which keeps the rule that every feature is explained, with its limits.
 */
enum class HelpTopic { Lock, Relationship, Upcoming, Checks, GettingStarted, Navigating, People, Events, Sharing, Relatives, Photos, Deleting, Backups, Restoring, Gedcom, Settings, Pro, Statistics, Privacy }

class HelpSection(val heading: String, val text: String)

/** A page of the in-app guide. [limits] says what the feature does not do, in plain words. */
class HelpPage(
    val topic: HelpTopic,
    val title: String,
    val summary: String,
    val sections: List<HelpSection>,
    val limits: List<String>,
)

/** The in-app guide. Keep it true to the app: when a feature changes, change its page in the same change. */
object HelpGuide {
    val pages: List<HelpPage> = listOf(
        HelpPage(
            topic = HelpTopic.GettingStarted,
            title = "Getting started",
            summary = "Build your tree one person at a time.",
            sections = listOf(
                HelpSection("Add the first person", "On the start screen choose Add the first person. A first name or a surname is enough; everything else is optional."),
                HelpSection("Add their relatives", "Use Parent, Sibling, Spouse and Child on the card at the bottom of the tree (Sibling shows once the person has a parent recorded). The new person is linked to the person on that card."),
                HelpSection("Quick add", "The Quick add button on the tree asks only for a name. Choose Child, Spouse, Parent or Sibling, type the name and tap Add; the box stays open so you can add several people. A name with two or more words uses the last word as the surname. Pick the sex and a birth date if you know them (both optional). A sibling gets both parents when the person's one recorded parent has one partner; with several partners you are asked which. You can fill in other details later with Edit."),
                HelpSection("Already have a tree somewhere else?", "Choose Import a GEDCOM file on the start screen. See \"Moving a tree in and out\"."),
                HelpSection("Just looking around?", "The start screen can fill the tree with an invented family. They are ordinary entries. A bar on the tree offers Clear sample, and Backup and sharing has Delete the whole tree, so you can start your own tree. Clear sample cannot be undone (sample people are invented, so no copy is kept); Delete the whole tree keeps a copy so it can be undone."),
                HelpSection("The welcome tour", "The first time you open Kinhold, five short pages say what it is: private, free to use and made for real families. Swipe or tap Next, or Skip. You can read it again any time from More, then Welcome tour. It is only an introduction: nothing in it changes your tree or your settings."),
                HelpSection("What the buttons on the card mean", "Parent, Sibling and Child use small pictures: a person with an arrow up is a parent, a person with an arrow down is a child, and two people side by side are a sibling (the button appears once the person has a parent recorded). Spouse is a heart."),
            ),
            limits = listOf(
                "A person can have up to four parents recorded: birth, adoptive, foster and step.",
                "Only a year is needed for a date. A day and month are optional. A date can also be marked about, before, after or between two years (see \"Adding and editing people\").",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Navigating,
            title = "Finding your way around",
            summary = "The tree shows one person at a time.",
            sections = listOf(
                HelpSection("The focus person", "The card with a green outline is the focus. Their parents and grandparents are above, their children and grandchildren below."),
                HelpSection("Tap a card", "Tapping anyone makes them the focus, so you can walk the tree one person at a time."),
                HelpSection("Fold the card", "The card at the bottom can be folded to just the name to give the tree more room: tap the arrow on its right, or swipe down on the name and photo. Swipe up on the folded card, or tap the arrow again, to open it."),
                HelpSection("Drag and pinch", "Drag to move around and pinch to zoom. A quick flick keeps gliding for a moment."),
                HelpSection("More relatives", "A green +3 under a card means three children are not drawn. A small arrow above a card means more ancestors exist. Tap that card to bring them into view."),
                HelpSection("The bar at the bottom", "Me brings the tree back to you (see \"Adding and editing people\" for how to say who you are). Find searches by name, place or notes and can jump to the oldest person: a result that matched a place or a note says which. More holds Backup and sharing (saving, restoring, merging and importing), Tree statistics, Settings and this Help guide. The round arrow button over the tree frames the focus person again."),
            ),
            limits = listOf(
                "The view shows three generations above the focus person and two below.",
                "Find ignores case and accents. Places and notes are searched from two typed letters, the places are the birth place, death place and address (not the places of events), and only the first 1,000 characters of each person's notes are searched.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.People,
            title = "Adding and editing people",
            summary = "What you can record about someone.",
            sections = listOf(
                HelpSection("What you can fill in", "First names, surname, sex, the date of birth and of death (a year is enough, the day and month are optional), the place of each, and notes. Only a name is required. Baptisms, jobs, moves, marriages and the like go in events, see \"Events, marriages and sources\"."),
                HelpSection("Places", "While you type a place of birth, a place of death or the place of an event, places already used in your tree that match are offered under the box. Tap one to fill it in, so the same place is spelled the same way every time. \"Lahore\" and \"lahore\" count as one place, and the spelling used most often is the one offered."),
                HelpSection("Dates", "Leave any part empty if you do not know it. A month needs a year and a day needs a month, and 30 February is refused. The death date cannot be before the birth date."),
                HelpSection("Choosing a date", "Tap a date box and a calendar opens. Tap a day to choose it and use the arrows for the next or previous month. To go further, tap the year at the top: choosing a year shows the months, and choosing a month shows its days. Tapping the month name opens the months directly. If you only know the month or the year, choose Month or Year only instead: then a scrolling list of years (newest first) and the month buttons appear, and typing a year still works. Years before 1600 cannot be chosen in the calendar and have to be typed under Year only."),
                HelpSection("Dates you are not sure of", "In the date picker, under How sure, choose About, Before, After or Between. Between asks for a second year, \"until\". They show in words (\"about 1850\", \"between 1850 and 1855\"), on tree cards as c. 1850, bef. 1850, aft. 1850 and 1850–1855, and travel in backups and GEDCOM files. The picker needs a year before it offers them."),
                HelpSection("Changing someone", "Choose Edit on the card at the bottom of the tree. When you change an existing person, Kinhold saves by itself about a second after your last change, so an existing person has no Save button: use the arrow at the top or Close at the bottom to leave. A green Saved pill appears at the top of the screen after each save. If something stops a save, such as a missing name or a date that is not real, a short Not saved note stays up with the reason. Closing the form also saves what is waiting. Because changes are kept as you go, Close does not undo them; Delete this person asks first and can be undone for a few seconds. A new person is only added when you press Save."),
                HelpSection("Born and died on the cards", "When only one date is known, a small cake shows a birth date and a candle shows a death date."),
                HelpSection("Phone, email, address and map pin", "In Edit you can add a phone number, an email address, an address and a map pin. For the phone number you choose the country from a list (the flag and code beside the box, starting with your phone's own country) and type the rest; Kinhold saves it as one number such as +92 3001234567, dropping a leading 0, so Call, Text and WhatsApp work without typing a code. A number saved earlier without a code keeps working as it was until you edit it. On the Details page, Call, Text, WhatsApp, Email and Map open your phone's own apps, and Map shows the pin (or searches the address) in your maps app. There are three ways to save a pin. (1) Find from address: type the address and tap Find from address; your phone looks it up (this needs the internet at that moment and a map service on the phone, and Kinhold itself needs no extra permission). (2) Choose on map: tap it, press and hold the place in your maps app, tap the Plus Code under About (a code such as H88G+PF7; this copies it), come back and tap Use it. Paste does the same from the clipboard. Kinhold turns the code into numbers by itself; a short code is placed near a pin already saved in the tree or the town in the Address box, so the first pin is easiest with an address typed in. (3) Type or paste latitude and longitude, or a long Google Maps link. Google Maps never shows latitude and longitude for a dropped pin, and a short Google Maps link (maps.app.goo.gl) holds no numbers, so neither is accepted."),
                HelpSection("Your own facts", "Under Your own facts in Edit, add as many label and value pairs as you like, such as Blood group, Nickname or Village. They show on the Details page."),
                HelpSection("Names in Urdu and other scripts", "Type names in any script, including Urdu, Arabic and Hindi. They show on the tree cards, are found by Find and are kept in backups, share files and GEDCOM files. For a second name in another script, add it under Your own facts, for example Name in Urdu."),
                HelpSection("Hijri dates", "The date picker has a Gregorian and a Hijri tab. The Hijri tab is a calendar of its own: tap a day, use the arrows for another month, or tap the year at the top to pick a year, then a month, then the day. The Gregorian date is filled in for you, and a full Gregorian date shows its Hijri date when you switch tabs. Kinhold stores the Gregorian day. The Hijri date is shown under the dates on the tree card and the Details page, and in the Details timeline, for every full date (year, month and day); a year alone cannot be converted. It is off by default and can be switched on in Settings, under Dates."),
                HelpSection("Dates and age", "Tap a date box to open the date picker: type or step the year, then touch a month and a day. A living person's age shows on their card and Details page, worked out from their birth date."),
                HelpSection("Who you are", "Open More, then Settings, then Who are you, and choose your own card. The tree then marks you with a YOU badge, opens on you, and the Me button at the bottom brings the tree back to you from anywhere."),
                HelpSection("Starting values", "A new child starts with their father's surname and a new spouse with the opposite sex. You can change both."),
                HelpSection("Adoptive, foster and step parents", "When you add a parent or a child, choose how they are linked: Birth, Adoptive, Foster or Step. On a person's Details page, Parents lists each parent with the kind of link, and Change lets you correct it. Adoptive parents count as ancestors in How are we related, foster and step parents do not."),
                HelpSection("Linking people already in the tree", "On a person's Details page choose Link person, then say whether the person you will pick is their parent, child or spouse, and pick them. The list leaves out anyone the link would make impossible: the person themselves, people already linked that way, a descendant as a parent, an ancestor as a child, and anyone who already has four parents. For a parent or child you also choose the kind of link. Two birth parents of one child are made a couple, as when you add a new parent."),
                HelpSection("Removing a link", "On the Details page, Parents, Children and Spouses and partners each list the people linked, with Unlink beside each. Unlinking removes only the link: nobody is deleted and everyone keeps their other links. A partnership that has a marriage or other couple event recorded cannot be unlinked until that event is deleted in Edit, because the events belong to the partnership."),
                HelpSection("Several marriages", "Choose + Spouse again to add more spouses. The person is drawn in the middle with their spouses on both sides, and each couple's children hang from their own line. When you add a child to someone with several spouses, you are asked who the other parent is."),
            ),
            limits = listOf(
                "Different people can share a name. Find shows whose child each one is.",
                "Place suggestions come from the birth and death places of the people in your tree (not from event places or addresses), at most the 2,000 most used, and only places you have typed before are offered: Kinhold has no list of the world's places and looks nothing up.",
                "Linking works one link at a time. When you link a child to a person who has a partner, Kinhold asks who the other parent is and links both; otherwise a child linked to one parent is not also linked to the other. A sibling is made by linking the same parents. There is no undo button for a link, but Unlink removes it again (a copy of the tree is only kept for restores, imports and merges).",
                "Spouses are placed by marriage date where you have recorded one, then by birth year, then by the order they were added.",
                "Kinhold asks for no permission to call or message: it only hands the number to your phone's apps. WhatsApp needs the number with its country code (the country list adds it) and only works if WhatsApp is installed.",
                "A pin is two numbers saved by you. Kinhold does not look addresses up, show a map inside the app or work out distances, and short Google links (maps.app.goo.gl) cannot be read: open them first and copy the numbers.",
                "Only one phone number, one email address and one address per person for now.",
                "Age is an estimate when only the birth year is known, and is shown only when the person may be alive (no death date, born in the last 110 years) or when both years are known.",
                "Dates are stored in the Gregorian calendar. A Hijri date needs a year, a month and a day and is converted when you choose it; the Umm al-Qura calendar covers 1300 to 1600 AH, and the Hijri date shown beside a Gregorian one can differ by a day from a calendar based on moon sighting.",
                "There is no separate name field for a second script. Matching people for merges and duplicates ignores accents and case, but does not translate a name between scripts.",
                "Only the focus person's own spouses are drawn. To see the spouse's other marriages or family, tap that spouse.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Events,
            title = "Events, marriages and sources",
            summary = "What happened in a life, and where you learned it.",
            sections = listOf(
                HelpSection("Adding an event", "Choose Edit on a person, then + Add an event under Events. Tap What happened to pick from the list (baptism, burial, job, move, military service, or Other with your own name), then set a date with the date picker, a place and notes. Every part of the date is optional."),
                HelpSection("Marriage and divorce", "Under each spouse's name in Edit is a place for events of that couple. Add a marriage with its date and place, and a divorce or annulment if there was one. Marriage dates put spouses in order in the tree, and a divorce stops a former partner being shown as a step-parent."),
                HelpSection("Sources", "A source is a note of where something came from: a book, a certificate, a website, with the page or entry. Add them to an event, or to the person in general, at the bottom of the Edit screen."),
                HelpSection("Seeing it all", "Choose Details on the card under the tree to see the focus person's contact details, notes, timeline and sources on one page."),
                HelpSection("The timeline", "On the Details page the timeline puts a person's life in date order: birth, each event, marriages and other couple events, the births of their children, and death, with how old they were at each. Events with no date come after the dated ones, an undated birth comes first and an undated death last. Burial, cremation and probate are listed after the death when the dates are the same. Nothing extra is stored; it is built from what you recorded."),
                HelpSection("Keeping it safe", "Events and sources are part of backups, GEDCOM files, Undo after a delete and Undo of a restore."),
            ),
            limits = listOf(
                "A person or a couple can have up to 500 events, and each event or person up to 50 sources. Long text is cut: about 5,000 characters in a note.",
                "Sources are plain text. You cannot attach a scan, photo or document to them.",
                "A date can be marked about, before, after or between two years, but only the years of a \"between\" date are kept, not its months and days. Other doubts, such as \"probably\", go in the notes.",
                "Events are shown on the Details page and in Edit, not on the tree cards. Birth and death stay as the fields on the form, and appear in the timeline on the Details page beside the events.",
                "An age in the timeline is exact only when both the birth date and the event date have a day and month; otherwise it says About, and it can be a year out. A child with no birth year is not shown in the timeline (they are still listed under Children).",
                "Events are ordered by date; events with no date come last.",
                "Only marriage, divorce and annulment change how the tree looks. A partner who died is not treated as an ended marriage.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Sharing,
            title = "Sharing with relatives",
            summary = "Send part of your tree to a relative, or merge theirs into yours.",
            sections = listOf(
                HelpSection("Which one do I need?", "Open More, then Backup and sharing. Keep my own tree safe: the Backup and Restore cards. Send some relatives to a family member: Send part of your tree. I received a file: Open a file from a relative. Move to or from another program such as Ancestry: the GEDCOM card. The screen starts with a short guide and groups its cards under these headings."),
                HelpSection("Sending part of your tree", "Open More, then Backup and sharing, then Send part of your tree and choose a person (or open Details on a person and choose Share). Pick which relatives (ancestors, descendants or both), how many generations, and whether to include spouses, photos and contact details. Then choose Send it to a relative to open your phone's share sheet and send the file by WhatsApp, email or any other app, or Save the file on this phone to keep it first. The chart can be sent as a picture the same way, with Send the picture."),
                HelpSection("What your relative does", "They open Kinhold, then More, then Backup and sharing, then Open a file from a relative, and choose the file. Kinhold says what kind of file it is, shows who they already have and who is new, and asks about anything that differs. They tap Add to my tree at the bottom, and can undo it. Nothing is removed from their tree."),
                HelpSection("A family sheet to print", "On the Share screen, Print a family sheet makes a PDF family group sheet for the person: one page for each partner, with the two of them, their marriage and their children, with dates and places, and further pages if there are many children. Save it, or send it by WhatsApp, email or any app. Children with no partner recorded go on a page of their own."),
                HelpSection("A web page for relatives without Kinhold", "On the Share screen, Send a web page makes one page that opens in any browser, with no app and no internet. It holds the same people as the share file (the choices above apply, including leaving out people who may be alive): a search box, and a page for each person with their parents, partners and children as links. Names, dates, places and notes only."),
                HelpSection("Opening a file you received", "Use Open a file from a relative for a share file, a Kinhold backup or a GEDCOM file. It only ever adds: it never removes anyone, and people already in your tree are matched and not added twice. To swap your whole tree for a backup instead, use Restore from a backup."),
                HelpSection("Is this the same person?", "People who travelled from one Kinhold to another are matched for sure. Others are suggested by name, birth year and relatives in common. A strong suggestion starts as the same person, a weaker one as a different person, and you can change either."),
                HelpSection("Who is the main person?", "A share file records the person it was made from. When you merge it, the first card says \"This branch starts with …\". If that person is already in your tree, Kinhold shows who it matched, and you can choose someone else. If they are not in your tree, choose Attach to someone, pick a person you have and say whether the new person is their parent, child, spouse or sibling. Everyone else in the file is placed around them."),
                HelpSection("Save a backup first", "Before a merge, an import or a restore, Kinhold offers to save a backup file of your tree. Save a backup does that and then carries on; Continue without skips it. A backup file is the safest way back if something goes wrong."),
                HelpSection("Fields that differ", "For people in both trees you see each field that differs. Where yours is empty or less exact it starts as use theirs, and where the two disagree it starts as keep mine. Choose for each field, or fill all your empty fields at once."),
                HelpSection("Undo", "A copy of your tree is kept first. Choose Undo this merge, or later Undo the last restore or import in Backup."),
                HelpSection("A picture or a PDF", "On the Share screen, Print or send a picture saves a chart of what the tree shows around that person, as a PDF to print or a PNG picture to send. It is drawn in light colours with dark text so it prints well."),
            ),
            limits = listOf(
                "Share files, like backups, are not encrypted. Send them only to people you trust.",
                "Only files made by a recent Kinhold say who their main person is. For an older share file, a backup or a GEDCOM file there is no \"branch starts with\" card, so people are matched by name, birth year and relatives only.",
                "You can attach the main person to a person you already have, but you cannot link two people already in your tree here. Attaching as a sibling needs the chosen person to have a parent recorded, and a person can still have at most four parents.",
                "The chart shows only what the tree screen shows: three generations above and two below the person, with names and years but no photos. It is a picture, so the people in it cannot be merged by anyone who receives it.",
                "Phone numbers, email addresses, addresses, map pins and your own custom facts are left out of a share file unless you switch on Contact details and own facts. The tree owner mark (This is me) is never sent.",
                "Leaving out people who may be alive is a guess: it leaves out anyone with no death date born in the last 110 years, and keeps people with no dates at all.",
                "Two people with the same name and no birth year or relatives in common are only offered as a possible match, never merged without your answer.",
                "A person can have up to four parents. If the file would give a person a fifth, that link is skipped and you are told how many.",
                "Matching is done again each time you merge. If you confirm a match by hand, merging an updated file later may ask you again.",
                "Opening a file adds and fills in. It does not delete anyone, and it does not remove events you have that the file lacks.",
                "The family sheet covers the person, each partner and their children (not grandchildren), with the marriage if one is recorded, and leaves out sources and photos. It is a plain PDF for printing and cannot be opened back into Kinhold.",
                "The web page holds names, dates, places and the first 1,500 characters of notes only, with no photos, contact details, events or sources. It is read-only: a relative cannot change it or merge it into their tree, and it is not encrypted, so anyone who has it can read it.",
                "Your choices on the review screen are not kept if you turn the phone while reviewing.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Relatives,
            title = "Siblings, half and step relatives",
            summary = "Relatives who are not drawn in the tree, listed under the focus person.",
            sections = listOf(
                HelpSection("Where to look", "Under the focus person's name, a row of small cards names their brothers and sisters, half-siblings, step-parents, stepchildren and step-siblings. Tap one to make them the focus."),
                HelpSection("How they are worked out", "A sibling has the same recorded parents. A half-sibling shares some of them, which happens when a parent has children with different partners. A step-parent is a parent's spouse who is not a parent, and a stepchild is a spouse's child who is not yours."),
                HelpSection("Adding them", "There is no separate button. Add a child to a parent, choosing the other parent when asked, and add a spouse to a parent, and the links appear by themselves."),
            ),
            limits = listOf(
                "A person with only one recorded parent shows everyone who shares that parent as a sibling, even if some are really half-siblings.",
                "A parent's spouse who is not recorded as a parent is shown as a step-parent. Foster and step parents you record yourself count as parents here but not as ancestors in How are we related.",
                "Siblings are listed, not drawn, and at most 30 relatives are shown.",
                "A couple only counts as ended if you record a divorce or annulment on it. Until then a parent's former partner who is not your parent is shown as a step-parent. A partner who died is not treated as ended.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Photos,
            title = "Photos",
            summary = "A picture for each person, and a default one when there is none.",
            sections = listOf(
                HelpSection("Adding a photo", "Open Edit (or add a new person). Next to the round picture are small round buttons: the picture frame chooses a photo from your gallery (through the phone's own photo picker, so Kinhold needs no access to your whole gallery) and the camera takes one. After you choose or take a picture, a round window shows how it will look; drag to move it, pinch or use the slider to zoom, then tap Use this. Press and hold any round button to see its name."),
                HelpSection("Changing or removing", "Once a photo is set, the crop button moves and zooms a picture chosen in this form again (it goes away once you leave the form), the picture frame or camera replaces it, and the red bin removes it. For an existing person the change is saved by itself a moment later; for a new person it is stored when you press Save."),
                HelpSection("Fitting the photo", "After you choose or take a picture, a round window shows how it will look. Drag to move it and pinch or use the slider to zoom, then tap Use this; only the part inside the circle is kept as the profile photo. Adjust photo does it again before you save. The extra photos on the Details page are kept whole."),
                HelpSection("More photos", "On a person's Details page, More photos shows a grid of extra pictures. Choose Add a photo or Take a photo, tap a picture to see it larger, and Remove to delete it. These save at once, without pressing Save."),
                HelpSection("People without a photo", "They show a default avatar, a head and shoulders in blue, rose or grey depending on the sex you recorded."),
                HelpSection("What is stored", "Kinhold keeps its own smaller copy of the picture inside the app, with a square version for the tree. The original stays where it was."),
            ),
            limits = listOf(
                "Take a photo opens your phone's own camera app and Kinhold keeps a smaller copy. Kinhold itself asks for no camera permission.",
                "A person can have at most 12 extra photos besides the main one. They are kept in backups and in share files when photos are included, but they are not part of GEDCOM files and are not merged into people who are already in your tree.",
                "Every photo makes backups larger, and automatic backups repeat them each time, so a tree with hundreds of photos has large backups.",
                "Pictures are shrunk to 720 pixels on the long side, so they are for faces and keepsakes, not printing.",
                "Backups include photos and can get large with many of them. GEDCOM files do not include photos.",
                "Deleting a person deletes their photo too. Undo brings it back, and so does a backup.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Deleting,
            title = "Deleting and undoing",
            summary = "Removing someone, and getting them back.",
            sections = listOf(
                HelpSection("Delete a person", "Open Edit, then choose Delete this person. You are asked to confirm first."),
                HelpSection("What is removed", "The person, their photo, their events and sources, and their links to parents, partners and children (with any marriage events of those couples). Their relatives stay in the tree."),
                HelpSection("Undo", "After a delete, a banner offers Undo for ten seconds. Undo puts the person back with their photo, events, sources and links to relatives who are still in the tree."),
            ),
            limits = listOf(
                "After ten seconds, or once you leave the app, the Undo button is gone. A backup is then the way to get someone back.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Backups,
            title = "Backups",
            summary = "Keep a copy of your whole tree in one file.",
            sections = listOf(
                HelpSection("What a backup is", "One file holding everyone in your tree, their photos, events and sources, and every link between them."),
                HelpSection("Saving one", "Open Backup, then Save a backup file, and choose where it goes: Google Drive, an email to yourself, your computer. Kinhold does not need access to your files for this."),
                HelpSection("Sending a copy", "Send a copy (Drive, email, WhatsApp) opens your phone's share sheet, so the file can go to Google Drive, an email to yourself or another app in one step. Kinhold makes the file, the app you pick receives it, and Kinhold itself sends nothing over the internet. A copy sent this way is not counted as a saved backup, because Kinhold cannot tell where it went; use Save a backup file for that."),
                HelpSection("Automatic backup", "Kinhold also saves a copy by itself shortly after you change your tree, and when you leave the app. Open More, then Backup and sharing, then Automatic backup. Copies are kept inside the app (the newest ten, and the newest of each of the last seven days). Choose a folder there and the same copies are also written to it, which keeps your tree even if the app is uninstalled. Restore the latest copy puts back the newest copy kept inside the app."),
                HelpSection("Reminders", "The tree screen shows when you last saved a backup. It turns amber when you have five or more people and no backup for a month."),
                HelpSection("Starting over", "Delete the whole tree (at the bottom of Backup and sharing, and Clear sample on a sample tree) removes everyone after you confirm. Kinhold keeps a copy first, and Undo the last restore, import or delete on the same screen brings it back until the next restore, import or delete."),
            ),
            limits = listOf(
                "The file is not encrypted. Anyone who has it can read your tree, so keep it private.",
                "The file made for the share sheet is kept inside Kinhold for half an hour and then deleted, but the app you send it to keeps its own copy, which Kinhold cannot remove.",
                "Copies inside the app are deleted when the app is uninstalled. Choose a folder in Automatic backup to keep copies outside the app, and move them off the phone (Google Drive, your computer) now and then, since a lost or broken phone loses a folder on it too.",
                "Android's own cloud backup is turned off for this app, and Kinhold sends nothing over the internet.",
                "Copies are made at most every 15 minutes, and not when nothing has changed. If your tree has many photos, each copy is large.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Restoring,
            title = "Restoring and undoing a restore",
            summary = "Bring a tree back from a backup file.",
            sections = listOf(
                HelpSection("Restoring", "Open Backup, then Choose a backup file. The whole file is checked before anything changes. If it is damaged or is not a Kinhold backup you are told, and your tree stays as it is."),
                HelpSection("What happens", "Your tree is replaced by the one in the file. A copy of your current tree is kept first."),
                HelpSection("Undo", "Undo the last restore or import brings that copy back. It swaps the two trees, so you can switch back again."),
            ),
            limits = listOf(
                "Only one copy is kept: the tree from just before the most recent restore or import.",
                "A restore replaces everything. It does not merge two trees.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Gedcom,
            title = "Moving a tree in and out",
            summary = "GEDCOM, the standard file for family trees.",
            sections = listOf(
                HelpSection("What GEDCOM is", "A file format that Ancestry, MyHeritage, FamilySearch and many other programs can export and open."),
                HelpSection("Importing", "Choose Import a GEDCOM file. You see how many people and families it holds and what cannot be kept, then choose to Import, or Add or Replace if you already have a tree."),
                HelpSection("Add or Replace", "Replace swaps your tree for the file's. Add keeps everyone you have and adds the file's people as new ones. Either can be undone."),
                HelpSection("Exporting", "Open Backup, then Save as a GEDCOM file."),
            ),
            limits = listOf(
                "Adoptive and foster links are read from and written to GEDCOM (the pedigree type); a step link is written with a type other programs may ignore. Events (marriage, divorce, baptism, burial, jobs, residences and more), full dates and source citations are kept. Dates marked ABT, EST, CAL, BEF, AFT, BET ... AND ... and FROM ... TO ... are kept as about, before, after and between dates; an end day or month, and a phrase in brackets, are not kept and are counted as simplified.",
                "Not kept: photos and other files, identity numbers, church ordinances, web addresses, source records beyond title, author and text, and events with no place here. Photos are not written to GEDCOM files either. The import tells you how many it skipped.",
                "Names: a suffix such as Jr. is kept at the end of the surname. A second name in the file (a married name, an alias) is kept as an own fact on the Details page, such as Married name. A person marked as dead with no date and no place gets the own fact Deceased, so they are not taken for living; their dates stay empty.",
                "Citations for a birth or a death become sources of the person, with a note saying which.",
                "Add does not match people who are already in your tree, so adding the same file twice makes duplicates.",
                "Very old files that use the ANSEL character set can show wrong accents.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Settings,
            title = "Settings",
            summary = "Dark or light, and the accent colour.",
            sections = listOf(
                HelpSection("Opening Settings", "Choose Settings at the top of the tree screen, or on the start screen."),
                HelpSection("Theme", "System, which follows your phone's own dark mode setting, or Light, or Dark."),
                HelpSection("Accent colour", "Pick one of six colours for buttons, highlights and the focus outline. Changes apply straight away and are remembered."),
                HelpSection("Version", "The version number is at the bottom of Settings. Holding your finger on it for ten seconds opens a hidden Developer page: who made the app, and a box where the author can type a private phrase to confirm authorship. It changes nothing."),
            ),
            limits = listOf(
                "Settings belong to this phone. They are not part of your tree, so backups and restores do not carry them.",
                "The photo avatars keep their blue, rose and grey colours whatever accent you choose.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Lock,
            title = "App lock",
            summary = "An optional PIN before Kinhold opens.",
            sections = listOf(
                HelpSection("Turning it on", "Open More, then Settings, then App lock, and choose Turn on. Pick a PIN of 4 to 8 digits and enter it twice. The lock is off until you do this."),
                HelpSection("How it works", "Kinhold asks for the PIN when it opens, and when you come back to it after the time you chose (immediately, 1 minute or 5 minutes). While the lock is on, Kinhold also hides its picture in the list of recent apps and does not allow screenshots."),
                HelpSection("Forgot the PIN?", "On the lock screen choose Forgot the PIN and prove who you are with your phone's own screen lock. The lock is then turned off, and you can set a new PIN in Settings."),
                HelpSection("Changing or turning it off", "In Settings, choose Change PIN or Turn off. You type the current PIN first."),
            ),
            limits = listOf(
                "The lock keeps people out of the app. It does not encrypt your tree, backup files or share files, which anyone who has them can read.",
                "If your phone has no screen lock, a forgotten PIN cannot be reset, and the only way back in is to clear Kinhold's data, which deletes the tree. Kinhold warns you before you turn the lock on, and a backup folder lets you restore.",
                "After five wrong tries in a row you must wait, 30 seconds at first and longer after more wrong tries.",
                "The PIN is stored on this phone as a scrambled value, not in your tree or in backups, so restoring a backup never changes the lock.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Relationship,
            title = "How are we related?",
            summary = "See how any two people in your tree are related.",
            sections = listOf(
                HelpSection("Finding a relationship", "Open More, then How are we related. Choose a first and a second person, and Kinhold says how the second is related to the first, such as uncle, first cousin or sister-in-law, and names the people in between."),
                HelpSection("Family words", "Choose a language under Family words, on this page or in Settings, and the word your family uses is shown beside the English one, here and on a person's Details page. The languages are Urdu and Punjabi, Hindi, Bengali, Arabic, Persian, Turkish and Spanish, written in Latin letters. English only is the default. Examples: chacha, tau, mamu, phupho and khala (Urdu and Punjabi), tío (Spanish), amca (Turkish), ibn amm (Arabic). Urdu and Punjabi also have a few words for in-laws."),
                HelpSection("On a person's page", "Once you have said who you are in Settings, a person's Details page shows how they are related to you."),
            ),
            limits = listOf(
                "It can only use the links in your tree. If a parent or a marriage is missing, two people who really are related may show as not connected.",
                "Family words need the right sex to be recorded, and the uncle's birth year to tell chacha from tau. When Kinhold cannot tell, it shows no family word or both.",
                "Only the common close relations have family words: parents, children, grandparents, grandchildren, siblings, uncles and aunts, nephews and nieces, first cousins, and husband and wife. Distant cousins have the English name only, and in-law words exist only in Urdu and Punjabi.",
                "The words in languages other than Urdu and Punjabi are a short, simple list in Latin letters; regions and families differ in what they say, so treat them as a guide. Where a language has one word for several relatives, that word is shown for each.",
                "Half-relations are named in English only where a half-sibling is involved.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Upcoming,
            title = "Upcoming dates and reminders",
            summary = "Birthdays, anniversaries and remembrance days.",
            sections = listOf(
                HelpSection("The list", "Open More, then Upcoming dates. It lists the next year of birthdays (for people who may be alive), the days people died (remembered each year) and marriage anniversaries, soonest first. Tap a row to open that person's Details page. For a birthday or anniversary where the person has a phone number saved, a WhatsApp button at the end of the row opens WhatsApp with a short wish already typed in. Nothing is sent until you press send in WhatsApp, and Kinhold itself sends nothing. For an anniversary the first partner with a number is used. Remembrance days have no button."),
                HelpSection("What it needs", "A date only appears if it has a day and a month. Add them in Edit with the date picker: the birth date for a birthday, the death date for a remembrance day, and a marriage event for an anniversary."),
                HelpSection("Morning reminder", "Turn the reminder on in the same screen. At about 9 in the morning Kinhold posts a notification if today or tomorrow has a date. Android asks your permission to show notifications the first time. On the lock screen it shows only a general line, not names."),
                HelpSection("Today on the tree", "When someone's birthday or remembrance day falls today, the tree screen shows a line under the title, such as Today: Hira Malik's birthday. Tap it to go to that person. It uses the same rules as the list and needs a day and a month."),
            ),
            limits = listOf(
                "The reminder is a little late at times, because Android may delay an alarm that does not need to be exact, and it works only while notifications are allowed for Kinhold.",
                "Birthdays are shown only for people with no death date who were born in the last 110 years. Anniversaries are not shown for a couple with a divorce or a death recorded.",
                "A birthday on 29 February is shown on 28 February in years that have no 29 February.",
                "Dates use the Gregorian calendar only. Hijri dates are not supported yet.",
                "The WhatsApp wish needs WhatsApp on the phone and the person's number saved with its country code (for example +92 300 1234567). The wish is plain English text you can change before sending.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Checks,
            title = "Checks and duplicates",
            summary = "Find data that cannot be right, and people entered twice.",
            sections = listOf(
                HelpSection("Missing parent links", "When a person has only one parent recorded, that parent has one partner, and the partner is the parent of some of the parent's other children, Kinhold shows a card at the top (for example when a mother was added as a spouse, not as a parent). Add as parent links her to all those children at once, which stops them showing as half-siblings and joins the lines on the tree. Unlink on a Details page reverses it."),
                HelpSection("Things to look at", "Open More, then Checks and duplicates. Kinhold lists data that looks impossible or unlikely: a child born before a parent, a parent who died before the child was born, a marriage before someone was born, someone who would be over 110 with no death date. Tap one to go to that person and fix it."),
                HelpSection("Possible duplicates", "People with the same name, a birth year within one year of each other and the same sex are listed as possible duplicates, with the reason. Choose Join, keep first or Join, keep second to fold one entry into the other, or Not the same to stop asking."),
                HelpSection("What joining does", "The entry you keep gets anything it lacks from the other: dates, places, notes, contact details, photo, events, sources, own facts, and every parent, child and partner link. The other entry is then removed. A copy of your tree is kept first, so you can use Undo in Backup and sharing."),
                HelpSection("Separate groups", "A tree is one group when everyone is linked to everyone else through parents, children and partners. Removing a link, deleting a parent or adding someone without linking them can split it into several groups, and the tree screen only shows the group of the person in focus. Checks lists every other group (its size, and the person it opens on) so none is forgotten; tap one to open it on the tree, then use Link person on a Details page to join it back. Tree statistics shows how many groups there are."),
            ),
            limits = listOf(
                "The checks only read your tree. Nothing is fixed for you, and a warning can be wrong: a parent who really was young, or a person who really lived past 110.",
                "Only people with the same surname are compared for duplicates, so someone entered under two different surnames will not be found.",
                "Parents and children, spouses, and people of different sex are never offered as duplicates.",
                "A person keeps at most four parents when joined, so an extra parent link from the removed entry is dropped.",
                "Dates with only a year are compared by year, so a marriage and a birth in the same year are not flagged.",
                "Groups are worked out from parent, child and partner links only, so cousins who share no recorded ancestor are separate groups. The 30 largest other groups are listed and the rest only counted.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Pro,
            title = "Kinhold Pro and ads",
            summary = "What the paid version changes, and what is always free.",
            sections = listOf(
                HelpSection("The plan", "Kinhold is meant to be free with light ads, plus a one-time Pro purchase that removes them. There is no subscription and nothing renews."),
                HelpSection("What stays free", "Everything that keeps your tree safe is never locked: saving and restoring backups, GEDCOM, sharing and merging, checks, the app lock and reminders."),
                HelpSection("Where ads may and may not appear", "Ads never appear over the tree, while you edit a person or an event, or while you record. They are planned only for the More, Help, Upcoming and Backup screens."),
            ),
            limits = listOf(
                "Neither ads nor buying Pro are in this version of the app yet, so there is nothing to buy and no ad is shown.",
                "When ads are added, Kinhold will need permission to use the internet, and this guide, the Privacy page and the store listing will say so.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Statistics,
            title = "Tree statistics",
            summary = "Counts and records about the whole tree.",
            sections = listOf(
                HelpSection("Opening it", "Open More, then Tree statistics. It shows how many people there are (men, women, may be alive, died), couples, how many generations deep the tree goes, people with a photo and people with no dates, the earliest and most recent birth, the longest life, the person with the most children, the average lifespan, the commonest surnames and places, and births by decade. Each notable person has a Show button that opens the tree on them."),
                HelpSection("How it is worked out", "Everything is counted from what you have recorded when the screen opens, on the phone, and nothing is stored or sent anywhere."),
            ),
            limits = listOf(
                "Someone with no birth year is left out of the decades, the earliest and most recent births and the lifespans. A lifespan needs both years, and a gap over 120 years is ignored as a mistake.",
                "May be alive means no death recorded and born in the last 110 years. It is a guess, not a fact.",
                "Places are counted exactly as typed, so \"Lahore\" and \"Lahore, Pakistan\" count separately, and only birth and death places are counted, not the places of events.",
                "Generations is the longest chain of recorded parents, so a tree of unconnected families shows the deepest one, not the total.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Privacy,
            title = "Privacy",
            summary = "Where your tree is kept.",
            sections = listOf(
                HelpSection("On this phone", "Your tree and its photos are stored on this phone only. Kinhold has no internet permission, so it cannot send them anywhere."),
                HelpSection("When it leaves the phone", "Only when you save a backup or GEDCOM file, or share one yourself."),
                HelpSection("Permissions", "Kinhold asks for no permission to use the internet. Only the optional morning reminder asks to show notifications and to set its daily alarm again after the phone restarts."),
            ),
            limits = listOf(
                "Unless you turn on the optional app lock, anyone who can open your phone and this app can see your tree.",
                "Backup and GEDCOM files are not encrypted.",
            ),
        ),
    )

    fun page(topic: HelpTopic): HelpPage = pages.first { it.topic == topic }
}
