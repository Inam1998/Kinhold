package com.inamibrahim.kinhold.model

/**
 * One topic per feature a person can use. Adding a feature means adding a topic here and a page in [HelpGuide]; a test
 * fails if a topic has no page, which keeps the rule that every feature is explained, with its limits.
 */
enum class HelpTopic { Lock, GettingStarted, Navigating, People, Events, Sharing, Relatives, Photos, Deleting, Backups, Restoring, Gedcom, Settings, Privacy }

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
                HelpSection("Add their relatives", "Use + Parent, + Spouse and + Child on the card at the bottom of the tree. The new person is linked to the person on that card."),
                HelpSection("Already have a tree somewhere else?", "Choose Import a GEDCOM file on the start screen. See \"Moving a tree in and out\"."),
                HelpSection("Just looking around?", "The start screen can fill the tree with an invented family. They are ordinary entries you can edit, delete or replace by restoring a backup."),
            ),
            limits = listOf(
                "A person can have at most two parents.",
                "Only a year is needed for a date. A day and month are optional, and some programs' \"about\" and \"between\" dates are not kept as such.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Navigating,
            title = "Finding your way around",
            summary = "The tree shows one person at a time.",
            sections = listOf(
                HelpSection("The focus person", "The card with a green outline is the focus. Their parents and grandparents are above, their children and grandchildren below."),
                HelpSection("Tap a card", "Tapping anyone makes them the focus, so you can walk the tree one person at a time."),
                HelpSection("Drag and pinch", "Drag to move around and pinch to zoom. A quick flick keeps gliding for a moment."),
                HelpSection("More relatives", "A green +3 under a card means three children are not drawn. A small arrow above a card means more ancestors exist. Tap that card to bring them into view."),
                HelpSection("The bar at the bottom", "Me brings the tree back to you (see \"Adding and editing people\" for how to say who you are). Find searches by name and can jump to the oldest person. More holds Backup and sharing (saving, restoring, merging and importing), Settings and this Help guide. The round arrow button over the tree frames the focus person again."),
            ),
            limits = listOf(
                "The view shows three generations above the focus person and two below.",
                "Find looks at names only, not places or notes.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.People,
            title = "Adding and editing people",
            summary = "What you can record about someone.",
            sections = listOf(
                HelpSection("What you can fill in", "First names, surname, sex, the date of birth and of death (a year is enough, the day and month are optional), the place of each, and notes. Only a name is required. Baptisms, jobs, moves, marriages and the like go in events, see \"Events, marriages and sources\"."),
                HelpSection("Dates", "Leave any part empty if you do not know it. A month needs a year and a day needs a month, and 30 February is refused. The death date cannot be before the birth date."),
                HelpSection("Changing someone", "Choose Edit on the card at the bottom of the tree."),
                HelpSection("Phone, email, address and map pin", "In Edit you can add a phone number, an email address, an address and a map pin. On the Details page, Call, Text, WhatsApp, Email and Map open your phone's own apps, and Map shows the pin (or searches the address) in your maps app. To save a pin, press and hold the place in Google Maps, copy the numbers shown and paste them in the Map pin box."),
                HelpSection("Your own facts", "Under Your own facts in Edit, add as many label and value pairs as you like, such as Blood group, Nickname or Village. They show on the Details page."),
                HelpSection("Dates and age", "Tap a date box to open the date picker: type or step the year, then touch a month and a day. A living person's age shows on their card and Details page, worked out from their birth date."),
                HelpSection("Who you are", "Open More, then Settings, then Who are you, and choose your own card. The tree then marks you with a YOU badge, opens on you, and the Me button at the bottom brings the tree back to you from anywhere."),
                HelpSection("Starting values", "A new child starts with their father's surname and a new spouse with the opposite sex. You can change both."),
                HelpSection("Several marriages", "Choose + Spouse again to add more spouses. The person is drawn in the middle with their spouses on both sides, and each couple's children hang from their own line. When you add a child to someone with several spouses, you are asked who the other parent is."),
            ),
            limits = listOf(
                "Different people can share a name. Find shows whose child each one is.",
                "Spouses are placed by marriage date where you have recorded one, then by birth year, then by the order they were added.",
                "Kinhold asks for no permission to call or message: it only hands the number to your phone's apps. WhatsApp needs the number with its country code, such as +92, and only works if WhatsApp is installed.",
                "A pin is two numbers saved by you. Kinhold does not look addresses up, show a map inside the app or work out distances, and short Google links (maps.app.goo.gl) cannot be read: open them first and copy the numbers.",
                "Only one phone number, one email address and one address per person for now.",
                "Age is an estimate when only the birth year is known, and is shown only when the person may be alive (no death date, born in the last 110 years) or when both years are known.",
                "The date picker works in the Gregorian calendar only.",
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
                HelpSection("Seeing it all", "Choose Details on the card under the tree to see dates, events, marriages and sources of the focus person on one page."),
                HelpSection("Keeping it safe", "Events and sources are part of backups, GEDCOM files, Undo after a delete and Undo of a restore."),
            ),
            limits = listOf(
                "A person or a couple can have up to 500 events, and each event or person up to 50 sources. Long text is cut: about 5,000 characters in a note.",
                "Sources are plain text. You cannot attach a scan, photo or document to them.",
                "Dates cannot say \"about\" or \"between\"; put that in the notes.",
                "Events are shown on the Details page and in Edit, not on the tree cards. Birth and death stay as the fields on the form, and appear on the Details page beside the events.",
                "Events are ordered by date; events with no date come last.",
                "Only marriage, divorce and annulment change how the tree looks. A partner who died is not treated as an ended marriage.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Sharing,
            title = "Sharing with relatives",
            summary = "Send part of your tree to a relative, or merge theirs into yours.",
            sections = listOf(
                HelpSection("Sending a branch", "Open Details on a person, then choose Share branch. Pick which relatives (ancestors, descendants or both), how many generations, and whether to include spouses and photos. Save the file and send it to your relative by any app, such as WhatsApp or email."),
                HelpSection("Merging a file you received", "Open Backup, then Choose a file to merge. Kinhold compares it with your tree and shows you what it found before anything changes. Nothing in your own tree is replaced unless you choose it."),
                HelpSection("Is this the same person?", "People who travelled from one Kinhold to another are matched for sure. Others are suggested by name, birth year and relatives in common. A strong suggestion starts as the same person, a weaker one as a different person, and you can change either."),
                HelpSection("Fields that differ", "For people in both trees you see each field that differs. Where yours is empty or less exact it starts as use theirs, and where the two disagree it starts as keep mine. Choose for each field, or fill all your empty fields at once."),
                HelpSection("Undo", "A copy of your tree is kept first. Choose Undo this merge, or later Undo the last restore or import in Backup."),
            ),
            limits = listOf(
                "Share files, like backups, are not encrypted. Send them only to people you trust.",
                "Phone numbers, email addresses, addresses, map pins and your own custom facts are left out of a share file unless you switch on Contact details and own facts. The tree owner mark (This is me) is never sent.",
                "Leaving out people who may be alive is a guess: it leaves out anyone with no death date born in the last 110 years, and keeps people with no dates at all.",
                "Two people with the same name and no birth year or relatives in common are only offered as a possible match, never merged without your answer.",
                "A person can have at most two parents. If the file gives a person a third parent, that link is skipped and you are told how many.",
                "Matching is done again each time you merge. If you confirm a match by hand, merging an updated file later may ask you again.",
                "Merging adds and fills in. It does not delete anyone, and it does not remove events you have that the file lacks.",
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
                "Adopted, foster and step links are not recorded as such. A parent's spouse who is not a parent is shown as a step-parent, and an adopted child is an ordinary child.",
                "Siblings are listed, not drawn, and at most 30 relatives are shown.",
                "A couple only counts as ended if you record a divorce or annulment on it. Until then a parent's former partner who is not your parent is shown as a step-parent. A partner who died is not treated as ended.",
            ),
        ),
        HelpPage(
            topic = HelpTopic.Photos,
            title = "Photos",
            summary = "A picture for each person, and a default one when there is none.",
            sections = listOf(
                HelpSection("Adding a photo", "Open Edit (or add a new person) and choose Add a photo. You pick a picture from your phone's photo picker; Kinhold does not need access to your whole gallery."),
                HelpSection("Changing or removing", "In the same place choose Change photo or Remove photo. Nothing changes until you press Save."),
                HelpSection("People without a photo", "They show a default avatar, a head and shoulders in blue, rose or grey depending on the sex you recorded."),
                HelpSection("What is stored", "Kinhold keeps its own smaller copy of the picture inside the app, with a square version for the tree. The original stays where it was."),
            ),
            limits = listOf(
                "Photos can only be chosen from your phone, not taken with the camera from inside Kinhold yet.",
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
                HelpSection("Automatic backup", "Kinhold also saves a copy by itself shortly after you change your tree, and when you leave the app. Open More, then Backup and sharing, then Automatic backup. Copies are kept inside the app (the newest ten, and the newest of each of the last seven days). Choose a folder there and the same copies are also written to it, which keeps your tree even if the app is uninstalled. Restore the latest copy puts back the newest copy kept inside the app."),
                HelpSection("Reminders", "The tree screen shows when you last saved a backup. It turns amber when you have five or more people and no backup for a month."),
            ),
            limits = listOf(
                "The file is not encrypted. Anyone who has it can read your tree, so keep it private.",
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
                "Events (marriage, divorce, baptism, burial, jobs, residences and more), full dates and source citations are kept. Dates such as \"about 1850\" or \"between 1800 and 1810\" keep only their first date.",
                "Not kept: photos and other files, identity numbers, church ordinances, email and web addresses, source records beyond title, author and text, and events with no place here. Photos are not written to GEDCOM files either. The import tells you how many it skipped.",
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
                HelpSection("Theme", "Dark, Light, or Phone, which follows your phone's own dark mode setting."),
                HelpSection("Accent colour", "Pick one of six colours for buttons, highlights and the focus outline. Changes apply straight away and are remembered."),
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
            topic = HelpTopic.Privacy,
            title = "Privacy",
            summary = "Where your tree is kept.",
            sections = listOf(
                HelpSection("On this phone", "Your tree and its photos are stored on this phone only. Kinhold has no internet permission, so it cannot send them anywhere."),
                HelpSection("When it leaves the phone", "Only when you save a backup or GEDCOM file, or share one yourself."),
            ),
            limits = listOf(
                "Unless you turn on the optional app lock, anyone who can open your phone and this app can see your tree.",
                "Backup and GEDCOM files are not encrypted.",
            ),
        ),
    )

    fun page(topic: HelpTopic): HelpPage = pages.first { it.topic == topic }
}
