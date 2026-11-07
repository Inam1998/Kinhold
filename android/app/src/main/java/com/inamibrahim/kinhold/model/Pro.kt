package com.inamibrahim.kinhold.model

/**
 * Where an advertisement could go. Only the places marked [allowed] ever show one. The rule from the project: ads never
 * appear over the tree view, while a person is being edited, or while recording. Those places are listed here with
 * `allowed = false` so the rule is written down once and a test keeps it true.
 */
enum class AdPlacement(val allowed: Boolean) {
    More(true),
    Upcoming(true),
    HelpList(true),
    Backup(true),
    Tree(false),
    PersonForm(false),
    EventEditor(false),
    Recording(false),
}

/** Decides whether an ad slot is drawn. */
object AdPolicy {
    /**
     * An ad slot shows only when the place allows ads, the person has not unlocked Pro, and ads are switched on for this
     * build. Until a real ad service is added, "ads on" is only true in debug builds, where the slot is a labelled placeholder.
     */
    fun shows(placement: AdPlacement, proUnlocked: Boolean, adsOn: Boolean): Boolean = placement.allowed && !proUnlocked && adsOn
}

/**
 * What Pro changes. Everything that keeps a tree safe stays free: saving and restoring backups, GEDCOM, sharing, merging,
 * checks and the app lock. Pro removes ads. Keep this list true to the Pro screen and its Help page.
 */
object ProFeatures {
    val included = listOf("No ads anywhere in the app", "One payment, no subscription, no renewal")
    val alwaysFree = listOf("Saving and restoring backups", "GEDCOM import and export", "Sharing and merging", "Checks and duplicates", "App lock and reminders")
}
